package com.lmax.disruptor;

/**
 * EventConsumers waitFor {@link Entry}s to become available for consumption from the {@link RingBuffer}
 */
public interface EventConsumer extends Runnable
{
    /**
     * Get the sequence upto which this EventConsumer has consumed {@link Entry}s
     *
     * @return the sequence of the last consumed {@link Entry}
     */
    long getSequence();

    /**
     * Get the Barrier on which this EventConsumer is waiting for {@link Entry}s
     *
     * @return the barrier being waited on.
     */
    ThresholdBarrier getBarrier();

    /**
     * Signal that this EventConsumer should stop when it has finished consuming at the next clean break.
     */
    void halt();
}
