package com.lmax.disruptor;

/**
 * Entries are the items exchanged via a RingBuffer.
 */
public interface Entry
{
    /**
     * Get the sequence numbers assigned to this item in the series.
     *
     * @return the sequence number
     */
    long getSequence();

    /**
     * Explicitly set the sequence number for this Entry and a CommitCallback for indicating when the producer is
     * finished with assigning data for exchange.
     *
     * @param sequence to be assigned to this Entry
     * @param commitCallback for signalling when the claimed Entry is available for consumers of a {@link RingBuffer}.
     */
    void setSequence(long sequence, RingBuffer.CommitCallback commitCallback);

    /**
     * Indicate that this entry has been updated and is now available to the consumers of a {@link RingBuffer}.
     */
    void commit();
}
