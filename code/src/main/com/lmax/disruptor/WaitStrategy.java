package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

/**
 * Strategy employed for making {@link EntryConsumer}s wait on a {@link RingBuffer}.
 */
public interface WaitStrategy
{
    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer}
     *
     * @param sequence to be waited on.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(long sequence) throws AlertException, InterruptedException;

    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer} with a timeout specified.
     *
     * @param sequence to be waited on.
     * @param timeout value to abort after.
     * @param units of the timeout value.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(long sequence, long timeout, TimeUnit units) throws AlertException, InterruptedException;

    /**
     * Alert {@link EntryConsumer}s that a change in status has occurred by causing an {@link AlertException} to be thrown.
     */
    void alert();

    /**
     * Check if an alert has been raised.  The method will return with no action if no alert has been raised.
     *
     * @throws AlertException if an alert has been raised.
     */
    void checkForAlert() throws AlertException;

    /**
     * Notify those waiting that the {@link RingBuffer} cursor has advanced.
     */
    void notifyConsumers();

    /**
     * Strategy options which are available to those waiting on a {@link RingBuffer}
     */
    enum Option
    {
        /** This strategy uses a condition variable inside a lock to block the consumer which saves CPU resource as the expense of lock contention. */
        BLOCKING
        {
            @Override
            public WaitStrategy newInstance(RingBuffer ringBuffer)
            {
                return new BlockingWaitStrategy(ringBuffer);
            }
        },

        /** This strategy calls Thread.yield() in a loop as a waiting strategy which reduces contention at the expense of CPU resource. */
        YIELDING
        {
            @Override
            public WaitStrategy newInstance(RingBuffer ringBuffer)
            {
                return new YieldingWaitStrategy(ringBuffer);
            }
        };

        /**
         * Used by the {@link com.lmax.disruptor.RingBuffer} as a polymorphic constructor.
         *
         * @param ringBuffer the {@link ThresholdBarrier} is waiting on.
         * @return a new instance of the WaitStrategy
         */
        abstract WaitStrategy newInstance(RingBuffer ringBuffer);
    }
}
