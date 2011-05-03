package com.lmax.disruptor;

/**
 * Base implementation provided for ease of use
 */
public abstract class AbstractEntry implements Entry
{
    private long sequence;
    private RingBuffer.CommitCallback commitCallback;

    /**
     * {@inheritDoc}
     */
    public void commit()
    {
        commitCallback.commit(sequence);
    }

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
    public void setSequence(final long sequence, final RingBuffer.CommitCallback commitCallback)
    {
        this.sequence = sequence;
        this.commitCallback = commitCallback;
    }
}
