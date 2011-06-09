package com.lmax.disruptor;

/**
 * Entries are the items exchanged via a RingBuffer.
 */
public interface Entry
{
    /**
     * Get the sequence number assigned to this item in the series.
     *
     * @return the sequence number
     */
    long getSequence();

    /**
     * Explicitly set the sequence number for this Entry and a CommitCallback for indicating when the producer is
     * finished with assigning data for exchange.
     *
     * @param sequence to be assigned to this Entry
     */
    void setSequence(long sequence);
}
