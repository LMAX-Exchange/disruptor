package com.lmax.commons.disruptor;

public interface EventConsumer extends Runnable
{
    long getSequence();
    ThresholdBarrier getBarrier();
    void halt();
}
