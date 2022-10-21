package com.example.drdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.example.drdp.dto.Result;
import com.example.drdp.entity.VoucherOrder;
import com.example.drdp.mapper.VoucherOrderMapper;
import com.example.drdp.service.ISeckillVoucherService;
import com.example.drdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.utils.RedisIdWorker;
import com.example.drdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.drdp.utils.RedisConstants.LOCK_ORDER_PREFIX;
import static com.example.drdp.utils.RedisConstants.SECKILL_STOCK_PREFIX;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    private IVoucherOrderService proxy;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private static final String queueName = "queue:order";
    private static final String groupName = "seckill";
    private static final String consumerName = "consume";

    /**
     * 从消息队列中获取订单消息
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit((Runnable) () -> {
            while (true) {
                try {
                    List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                            Consumer.from(groupName, consumerName),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    if (recordList == null || recordList.isEmpty()) {
                        continue;
                    }
                    consumAndAck(recordList);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    handlerPendingList();
                }
            }
        });
    }

    /**
     * 消费消息，消费完成后确认消费完成
     */
    private void consumAndAck(List<MapRecord<String, Object, Object>> recordList) {
        MapRecord<String, Object, Object> record = recordList.get(0);
        Map<Object, Object> objectMap = record.getValue();
        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(objectMap, new VoucherOrder(), true);
        handlerVoucherOrder(voucherOrder);
        stringRedisTemplate.opsForStream().acknowledge(queueName, groupName, record.getId());
    }

    /**
     * 从 pendingList 中获取订单消息
     */
    private void handlerPendingList() {
        int flag = 0;
        while (true) {
            try {
                List<MapRecord<String, Object, Object>> recordList = stringRedisTemplate.opsForStream().read(
                        Consumer.from(groupName, consumerName),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                if (recordList == null || recordList.isEmpty()) {
                    break;
                }
                consumAndAck(recordList);
            } catch (Exception e) {
                log.error("处理pendingList订单异常", e);
                if (flag++ > 2) {
                    log.error("未能处理成功pendingList订单异常", e);
                    break;
                }
            }
        }
    }

    /**
     * 处理优惠券下单
     */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建锁对象，获取锁
        RLock lock = redissonClient.getLock(LOCK_ORDER_PREFIX + userId);
        if (!lock.tryLock()) {
            log.error("请勿重复下单！");
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        String beginTime = String.valueOf(stringRedisTemplate.opsForHash().get(
                SECKILL_STOCK_PREFIX + voucherId, "beginTime"));
        String endTime = String.valueOf(stringRedisTemplate.opsForHash().get(
                SECKILL_STOCK_PREFIX + voucherId, "endTime"));
        if (JSONUtil.toBean(beginTime, LocalDateTime.class).isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (JSONUtil.toBean(endTime, LocalDateTime.class).isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束！");
        }

        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");

        Long success = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId),
                queueName
        );
        if (success == null) {
            throw new RuntimeException("系统异常，请稍后重试");
        }
        if (success != 1) {
            return Result.fail(success == -1 ? "请勿重复下单！" : "优惠券已被抢空！");
        }

        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    /**
     * 创建优惠券订单
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        // 一人一单
        if (query().eq("user_id", voucherOrder.getUserId())
                .eq("voucher_id", voucherOrder.getVoucherId()).exists()) {
            log.error("该优惠券仅限购买一次！");
            return;
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("优惠券已被抢空！");
            return;
        }
        // 保存订单
        save(voucherOrder);
    }
}

