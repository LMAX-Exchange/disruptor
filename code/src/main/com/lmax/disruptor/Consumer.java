package com.lmax.disruptor;

/**
 * EntryConsumers waitFor {@link AbstractEntry}s to become available for consumption from the {@link RingBuffer}
 */
public interface Consumer extends Runnable
{
    /**
     * Get the sequence up to which this Consumer has consumed {@link AbstractEntry}s
     *
     * @return the sequence of the last consumed {@link AbstractEntry}
     */
    long getSequence();

    /**
     * Signal that this Consumer should stop when it has finished consuming at the next clean break.
     * It will call {@link ConsumerBarrier#alert()} to notify the thread to check status.
     */
    void halt();
}
