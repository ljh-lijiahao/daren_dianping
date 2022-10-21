package com.example.drdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 开始时间戳
     */
    private final static long BEGIN_TIMESTAMP = LocalDateTime.of(
            2022, 1, 1, 0, 0, 0).toEpochSecond(ZoneOffset.UTC);
    /**
     * 序列号位数
     */
    private final static int INCREMENT_BITS = 32;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public Long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
        // 时间戳
        long timestamp = nowTimestamp - BEGIN_TIMESTAMP;
        // 当前日期，精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 自增长序列号
        long increment = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data);
        return timestamp << INCREMENT_BITS | increment;
    }

}
