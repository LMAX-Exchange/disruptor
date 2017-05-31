package com.lmax.disruptor;

public interface BatchStartAware
{
    void onBatchStart(final long batchSize);
}
