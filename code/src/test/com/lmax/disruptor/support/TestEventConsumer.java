package com.lmax.disruptor.support;


import com.lmax.disruptor.EventConsumer;
import com.lmax.disruptor.ThresholdBarrier;

public final class TestEventConsumer
    implements EventConsumer
{
    private volatile long sequence = -7;

    public TestEventConsumer(final long initialSequence)
    {
        sequence = initialSequence;
    }

    @Override
    public long getSequence()
    {
        return sequence;
    }

    @Override
    public ThresholdBarrier getBarrier()
    {
        return null;
    }

    @Override
    public void halt()
    {
    }

    @Override
    public void run()
    {
    }
}
