package com.lmax.disruptor;

/**
 * Base implementation that must be extended for {@link RingBuffer} entries.
 */
public abstract class AbstractEntry
{
    private long sequence;

    /**
     * Get the sequence number assigned to this item in the series.
     *
     * @return the sequence number
     */
    public final long getSequence()
    {
        return sequence;
    }

    /**
     * Explicitly set the sequence number for this Entry and a CommitCallback for indicating when the producer is
     * finished with assigning data for exchange.
     *
     * @param sequence to be assigned to this Entry
     */
    final void setSequence(final long sequence)
    {
        this.sequence = sequence;
    }
}
