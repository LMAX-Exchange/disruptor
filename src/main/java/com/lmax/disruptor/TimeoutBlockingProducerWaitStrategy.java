package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Blocking strategy that uses {@link LockSupport#parkNanos(long)} for parking producer thread for 1 nanosecond
 * when the timeout reaches out, <code>RuntimeTimeoutException</code> is thrown
 */
public final class TimeoutBlockingProducerWaitStrategy implements ProducerWaitStrategy
{
    private final long timeoutInNanos;

    /**
     * @param timeout how long to wait before throwing {@link RuntimeTimeoutException}
     * @param units the unit in which timeout is specified
     */
    public TimeoutBlockingProducerWaitStrategy(final long timeout, final TimeUnit units)
    {
        this.timeoutInNanos = units.toNanos(timeout);
    }

    /**
     * @see ProducerWaitStrategy#await(long)
     */
    @Override
    public void await(final long claimedAt)
    {
        LockSupport.parkNanos(1L);
        if ((System.nanoTime() - claimedAt) >= timeoutInNanos)
        {
            throw new RuntimeTimeoutException("The ring buffer is full. Could not get a next sequence in the specified timeout " + timeoutInNanos + " nanoseconds");
        }
    }

    /**
     * @see ProducerWaitStrategy#getClaimedAtNanos()
     */
    @Override
    public long getClaimedAtNanos()
    {
        return System.nanoTime();
    }

}
