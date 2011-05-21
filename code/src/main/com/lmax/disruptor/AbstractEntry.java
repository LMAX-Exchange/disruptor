package com.lmax.disruptor;

/**
 * Base implementation provided for ease of use
 */
public abstract class AbstractEntry implements Entry
{
    private long sequence;

    /**
     * {@inheritDoc}
     */
    @Override
    public long getSequence()
    {
        return sequence;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSequence(final long sequence)
    {
        this.sequence = sequence;
    }
}
