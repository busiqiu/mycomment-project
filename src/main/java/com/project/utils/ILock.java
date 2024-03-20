package com.project.utils;

public interface ILock {
    /**
     * 尝试回去锁
     * @param timeoutSec
     * @return
     */
    boolean trylock(long timeoutSec);
    /**
     * 释放锁
     */
    void unlock();
}
