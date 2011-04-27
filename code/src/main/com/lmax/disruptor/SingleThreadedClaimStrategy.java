package com.lmax.disruptor;

final class SingleThreadedClaimStrategy
    implements ClaimStrategy
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
