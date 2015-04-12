package com.lmax.disruptor;

public interface TimeoutHandler
{
    void onTimeout(long sequence) throws Exception;
}
