package com.lmax.disruptor.support;


import com.lmax.disruptor.EntryConsumer;
import com.lmax.disruptor.ThresholdBarrier;

public final class TestEntryConsumer
    implements EntryConsumer
{
    private volatile long sequence = -7;

    public TestEntryConsumer(final long initialSequence)
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
