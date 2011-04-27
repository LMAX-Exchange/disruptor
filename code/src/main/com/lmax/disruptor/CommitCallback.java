package com.lmax.disruptor;

/**
 * Callback into {@link RingBuffer} to signal that the producer has populated the {@link Entry}
 * and it is not ready for use.
 */
interface CommitCallback
{
    /**
     * Callback to signal {@link Entry} is ready for consumption.
     *
     * @param sequence of the {@link Entry} that is ready for consumption.
     */
    public void commit(long sequence);
}
