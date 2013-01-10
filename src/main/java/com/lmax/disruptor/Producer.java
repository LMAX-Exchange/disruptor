package com.lmax.disruptor;

public interface Producer<T>
{
    T next();
    void publish();
    long currentSequence();
}
