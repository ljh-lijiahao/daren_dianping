package com.example.drdp.utils;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.example.drdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.example.drdp.utils.RedisConstants.*;


@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    public <T, ID> T queryWithPassThrough(
            String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 查询 redis 中商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if ("".equals(json)) {
            return null;
        }
        T t = dbFallback.apply(id);
        if (t == null) {
            // 存储空值到 redis 中，防止无效的访问数据库
            set(key, "", 2L, unit);
            return null;
        }
        // 保存商铺信息到 redis 中
        set(key, t, time, unit);
        return t;
    }

    public <T, ID> T queryWithMutex(
            String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 查询 redis 中商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        if ("".equals(json)) {
            return null;
        }
        String lockKey = LOCK_SHOP_PREFIX + id;
        T t = null;
        try {
            // 获取互斥锁
            if (!tryLock(lockKey)) {
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }
            t = dbFallback.apply(id);
            if (t == null) {
                set(key, "", time, unit);
                return null;
            }
            // 保存商铺信息到 redis 中
            set(key, JSONUtil.toJsonStr(t), time, unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放互斥锁
            unLock(lockKey);
        }
        return t;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <T, ID> T queryWithLogicalExpire(
            String keyPrefix, ID id, Class<T> type, Function<ID, T> dbFallback, Long time, TimeUnit unit) {

        String key = keyPrefix + id;
        // 查询 redis 中商铺信息
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        // 判断是否逻辑过期
        if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
            return t;
        }
        String lockKey = LOCK_SHOP_PREFIX + id;
        if (!tryLock(lockKey)) {
            // 二次检查
            json = stringRedisTemplate.opsForValue().get(key);
            redisData = JSONUtil.toBean(json, RedisData.class);
            t = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            // 判断是否逻辑过期
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                return t;
            }
            try {
                // 开启独立线程，实现缓存重建
                CACHE_REBUILD_EXECUTOR.submit(() -> {
                    setWithLogicalExpire(key, dbFallback.apply(id), time, unit);
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                unLock(lockKey);
            }
        }
        return t;
    }

    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(
                key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
