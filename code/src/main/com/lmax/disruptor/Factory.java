package com.lmax.disruptor;

public interface Factory<T>
{
    T create();
}
