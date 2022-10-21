package com.example.drdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.dto.Result;
import com.example.drdp.entity.Voucher;
import com.example.drdp.mapper.VoucherMapper;
import com.example.drdp.entity.SeckillVoucher;
import com.example.drdp.service.ISeckillVoucherService;
import com.example.drdp.service.IVoucherService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

import static com.example.drdp.utils.RedisConstants.SECKILL_STOCK_PREFIX;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
        // 保存秒杀库存、开始时间、截止时间到 redis 中
        stringRedisTemplate.opsForHash().putAll(
                SECKILL_STOCK_PREFIX + voucher.getId(), Map.of(
                        "stock", seckillVoucher.getStock().toString(),
                        "beginTime", JSONUtil.toJsonStr(seckillVoucher.getBeginTime()),
                        "endTime", JSONUtil.toJsonStr(seckillVoucher.getEndTime())
                ));
    }
}
