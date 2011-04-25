package com.lmax.disruptor;

public interface EventConsumer extends Runnable
{
    long getSequence();
    ThresholdBarrier getBarrier();
    void halt();
}
