package com.example.drdp.service.impl;

import com.example.drdp.dto.Result;
import com.example.drdp.entity.Shop;
import com.example.drdp.mapper.ShopMapper;
import com.example.drdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.drdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.example.drdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result saveShop(Shop shop) {
        // 写入数据库
        save(shop);
        Long shopId = shop.getId();
        cacheClient.setWithLogicalExpire(CACHE_SHOP_PREFIX + shopId, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 返回店铺id
        return Result.ok(shopId);
    }

    /**
     * 根据id查询商铺信息
     */
    @Override
    public Result queryById(Long id) {
        // 缓存击穿
/*
        Shop shop = cacheClient.queryWithPassThrough(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/
        // 互斥锁解决缓存击穿
/*
        Shop shop = cacheClient.queryWithMutex(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
*/
        // 逻辑过期解决缓存击穿
        Shop shop = cacheClient.queryWithLogicalExpire(
                CACHE_SHOP_PREFIX, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if (shop == null) {
            return Result.fail("商铺不存在，请稍后重试");
        }
        return Result.ok(shop);
    }

/*
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_PREFIX + id;
        // 查询 redis 中商铺信息
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJSON)) {
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        if ("".equals(shopJSON)) {
            return null;
        }
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 保存商铺信息到 redis 中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
*/
/*
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_PREFIX + id;
        // 查询 redis 中商铺信息
        String shopJSON = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJSON)) {
            return JSONUtil.toBean(shopJSON, Shop.class);
        }
        if ("".equals(shopJSON)) {
            return null;
        }
        String lockKey = LOCK_SHOP_PREFIX + id;
        Shop shop = null;
        try {
            // 获取互斥锁
            if (!tryLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 保存商铺信息到 redis 中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }
*/
/*
    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_PREFIX + id;
        // 查询 redis 中商铺信息
        String redisJSON = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(redisJSON)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(redisJSON, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        // 判断是否逻辑过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return shop;
        }
        String lockKey = LOCK_SHOP_PREFIX + id;
        if (!tryLock(lockKey)) {
            // 二次检查
            redisData = JSONUtil.toBean(redisJSON, RedisData.class);
            // 判断是否逻辑过期
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            }
            // 开启独立线程，实现缓存重建
            try {
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    saveShopToRedis(id, 20L);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }finally {
                unLock(key);
            }
        }
        return shop;
    }
*/

/*
    public void saveShopToRedis(Long id, Long expireSeconds) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_PREFIX + id, JSONUtil.toJsonStr(redisData));
    }
*/

    /**
     * 更新商铺信息
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("商铺id不存在，不能修改");
        }
        updateById(shop);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_PREFIX + shopId, shop, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }
}
