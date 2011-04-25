package com.lmax.commons.disruptor;

/**
 * Base implementation provided for ease of use
 */
public abstract class AbstractEntry implements Entry
{
    private long sequence;
    private CommitCallback commitCallback;

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
    public void setSequence(long sequence, CommitCallback commitCallback)
    {
        this.sequence = sequence;
        this.commitCallback = commitCallback;
    }
}
