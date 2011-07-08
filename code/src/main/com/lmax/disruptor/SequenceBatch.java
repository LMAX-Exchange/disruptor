package com.lmax.disruptor;

/**
 * Used to record the batch of sequences claimed in a {@link RingBuffer}.
 */
public final class SequenceBatch
{
    private final int size;
    private long end = RingBuffer.INITIAL_CURSOR_VALUE;

    /**
     * Create a holder for tracking a batch of claimed sequences in a {@link RingBuffer}
     * @param size of the batch to claim.
     */
    public SequenceBatch(final int size)
    {
        this.size = size;
    }

    /**
     * Get the end sequence of a batch.
     *
     * @return the end sequence in a batch
     */
    public long getEnd()
    {
        return end;
    }

    /**
     * Set the end of the batch sequence.  To be used by the {@link ProducerBarrier}.
     *
     * @param end sequence in the batch.
     */
    void setEnd(final long end)
    {
        this.end = end;
    }

    /**
     * Get the size of the batch.
     *
     * @return the size of the batch.
     */
    public int getSize()
    {
        return size;
    }

    /**
     * Get the starting sequence for a batch.
     *
     * @return the starting sequence of a batch.
     */
    public long getStart()
    {
        return end - (size - 1L);
    }
}
