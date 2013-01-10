package com.lmax.disruptor;

public interface DataSource<T>
{
    T getPublished(long sequence);
}
