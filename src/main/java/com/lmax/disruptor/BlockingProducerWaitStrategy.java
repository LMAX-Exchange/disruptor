package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * Blocking strategy that uses {@link LockSupport#parkNanos(long)} for parking producer thread for 1 nanosecond
 * It is a default wait strategy for producer threads
 */
public final class BlockingProducerWaitStrategy implements ProducerWaitStrategy
{

    /**
     * @see ProducerWaitStrategy#await(long)
     */
    @Override
    public void await(final long startedAt)
    {
        LockSupport.parkNanos(1L);
    }
}
