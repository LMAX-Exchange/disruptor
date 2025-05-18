package com.lmax.disruptor;

/**
 * Strategy employed for making producer thread wait for an available slot in ring buffer.
 */

public interface ProducerWaitStrategy
{
    /**
     *
     * @param startedAtNanos Time in nanoseconds when a producer thread has tried to get the next available sequence for a first time
     */
    void await(long startedAtNanos);
}
