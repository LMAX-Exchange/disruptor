package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLong;

final class MultiThreadedClaimStrategy
    implements ClaimStrategy
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

    @Override
    public void waitForCursor(final long sequence, final RingBuffer ringBuffer)
    {
        while (ringBuffer.getCursor() != sequence)
        {
            // busy spin
        }
    }
}
