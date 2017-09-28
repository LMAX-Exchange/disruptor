package com.lmax.disruptor;

public interface BatchStartAware
{
    void onBatchStart(long batchSize);
}
