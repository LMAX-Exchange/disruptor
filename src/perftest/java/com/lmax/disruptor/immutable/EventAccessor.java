package com.lmax.disruptor.immutable;

public interface EventAccessor<T>
{
    T take(long sequence);
}
