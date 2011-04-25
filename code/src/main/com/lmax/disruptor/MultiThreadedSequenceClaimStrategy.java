package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLong;

final class MultiThreadedSequenceClaimStrategy
    implements SequenceClaimStrategy
{
    private final AtomicLong sequence = new AtomicLong(0);

    @Override
    public long getAndIncrement()
    {
        return sequence.getAndIncrement();
    }

    @Override
    public void setSequence(final long sequence)
    {
        this.sequence.set(sequence);
    }
}
