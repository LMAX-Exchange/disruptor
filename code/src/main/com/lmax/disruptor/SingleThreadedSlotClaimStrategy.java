package com.lmax.disruptor;

final class SingleThreadedSlotClaimStrategy
    implements SlotClaimStrategy
{
    private long sequence;

    @Override
    public long getAndIncrement()
    {
        return sequence++;
    }

    @Override
    public void setSequence(final long sequence)
    {
        this.sequence = sequence;
    }
}
