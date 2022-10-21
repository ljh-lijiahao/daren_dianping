package com.example.drdp.utils.lock;

public interface ILock {
    boolean tryLock(Long timeoutSec);

    void unlock();
}
