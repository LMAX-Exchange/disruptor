package com.lmax.disruptor;

public interface DataProvider<T>
{
    T getPublished(long sequence);
}
