package com.lmax.disruptor;

final class SingleThreadedSequenceClaimStrategy
    implements SequenceClaimStrategy
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
