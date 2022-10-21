package com.example.drdp.utils.lock;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private String lockKey;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String THREAD_PREFIX = UUID.randomUUID().toString(true);
    private String threadId;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }


    public SimpleRedisLock(String lockKey, StringRedisTemplate stringRedisTemplate) {
        this.lockKey = lockKey;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        // 线程标识
        threadId = THREAD_PREFIX + "-" + Thread.currentThread().getId();
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(
                lockKey, threadId, timeoutSec, TimeUnit.SECONDS));
    }

    @Override
    public void unlock() {
        // 调用 lua 脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT, Collections.singletonList(lockKey), threadId);
    }
/*
    @Override
    public void unlock() {
        String value = stringRedisTemplate.opsForValue().get(LOCK_PREFIX + keyName);
        if (threadId.equals(value)) {
            stringRedisTemplate.delete(LOCK_PREFIX + keyName);
        }
    }
*/
}
