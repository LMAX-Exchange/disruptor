package com.lmax.disruptor;

interface Publisher
{
    void publish(long sequence);

    boolean isAvailable(long sequence);

    void ensureAvailable(long sequence);

    void forcePublish(long sequence);
}
