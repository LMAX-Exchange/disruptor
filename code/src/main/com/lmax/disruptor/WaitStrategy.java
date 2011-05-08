package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.lmax.disruptor.AlertException.ALERT_EXCEPTION;

/**
 * Strategy employed for making {@link EntryConsumer}s wait on a {@link RingBuffer}.
 */
public interface WaitStrategy
{
    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer}
     *
     * @param ringBuffer on which to wait.
     * @param sequence to be waited on.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(RingBuffer ringBuffer, long sequence) throws AlertException, InterruptedException;

    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer} with a timeout specified.
     *
     * @param ringBuffer on which to wait.
     * @param sequence to be waited on.
     * @param timeout value to abort after.
     * @param units of the timeout value.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(RingBuffer ringBuffer, long sequence, long timeout, TimeUnit units) throws AlertException, InterruptedException;

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
            public WaitStrategy newInstance()
            {
                return new BlockingStrategy();
            }
        },

        /** This strategy calls Thread.yield() in a loop as a waiting strategy which reduces contention at the expense of CPU resource. */
        YIELDING
        {
            @Override
            public WaitStrategy newInstance()
            {
                return new YieldingStrategy();
            }
        },

        /** This strategy calls spins in a loop as a waiting strategy which is lowest and most consistent latency but ties up a CPU */
        BUSY_SPIN
        {
            @Override
            public WaitStrategy newInstance()
            {
                return new BusySpinStrategy();
            }
        };

        /**
         * Used by the {@link com.lmax.disruptor.RingBuffer} as a polymorphic constructor.
         *
         * @return a new instance of the WaitStrategy
         */
        abstract WaitStrategy newInstance();
    }

    /**
     * Blocking strategy that uses locks and a condition variable for
     * {@link com.lmax.disruptor.EntryConsumer}s waiting on a barrier.
     *
     * This strategy should be used when performance and low-latency are not as important
     * as CPU resource.
     */
    static final class BlockingStrategy implements WaitStrategy
    {
        private final Lock lock = new ReentrantLock();
        private final Condition consumerNotifyCondition = lock.newCondition();

        private volatile boolean alerted = false;


        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence)
            throws AlertException, InterruptedException
        {
            if (ringBuffer.getCursor() < sequence)
            {
                lock.lock();
                try
                {
                    while (ringBuffer.getCursor() < sequence)
                    {
                        checkForAlert();
                        consumerNotifyCondition.await();
                    }
                }
                finally
                {
                    lock.unlock();
                }
            }

            return ringBuffer.getCursor();
        }

        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            if (ringBuffer.getCursor() < sequence)
            {
                lock.lock();
                try
                {
                    while (ringBuffer.getCursor() < sequence)
                    {
                        checkForAlert();
                        if (!consumerNotifyCondition.await(timeout, units))
                        {
                            break;
                        }
                    }
                }
                finally
                {
                    lock.unlock();
                }
            }

            return ringBuffer.getCursor();
        }

        @Override
        public void checkForAlert() throws AlertException
        {
            if (alerted)
            {
                alerted = false;
                throw ALERT_EXCEPTION;
            }
        }

        @Override
        public void alert()
        {
            alerted = true;
            notifyConsumers();
        }

        @Override
        public void notifyConsumers()
        {
            lock.lock();
            try
            {
                consumerNotifyCondition.signalAll();
            }
            finally
            {
                lock.unlock();
            }
        }
    }

    /**
     * Yielding strategy that uses a Thread.yield() for
     * {@link com.lmax.disruptor.EntryConsumer}s waiting on a barrier.
     *
     * This strategy is a good compromise between performance and CPU resource.
     */
    static final class YieldingStrategy implements WaitStrategy
    {
        private volatile boolean alerted = false;

        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence)
            throws AlertException, InterruptedException
        {
            while (ringBuffer.getCursor() < sequence)
            {
                checkForAlert();
                Thread.yield();
            }

            return ringBuffer.getCursor();
        }

        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();

            while (ringBuffer.getCursor() < sequence)
            {
                checkForAlert();
                Thread.yield();
                if (timeoutMs < (System.currentTimeMillis() - currentTime))
                {
                    break;
                }
            }

            return ringBuffer.getCursor();
        }

        @Override
        public void checkForAlert() throws AlertException
        {
            if (alerted)
            {
                alerted = false;
                throw ALERT_EXCEPTION;
            }
        }

        @Override
        public void alert()
        {
            alerted = true;
            notifyConsumers();
        }

        @Override
        public void notifyConsumers()
        {
        }
    }

    /**
     * Busy Spin strategy that uses a busy spin loop for
     * {@link com.lmax.disruptor.EntryConsumer}s waiting on a barrier.
     *
     * This strategy is a good compromise between performance and CPU resource.
     */
    static final class BusySpinStrategy implements WaitStrategy
    {
        private volatile boolean alerted = false;

        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence)
            throws AlertException, InterruptedException
        {
            while (ringBuffer.getCursor() < sequence)
            {
                checkForAlert();
            }

            return ringBuffer.getCursor();
        }

        @Override
        public long waitFor(final RingBuffer ringBuffer, final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();

            while (ringBuffer.getCursor() < sequence)
            {
                checkForAlert();
                if (timeoutMs < (System.currentTimeMillis() - currentTime))
                {
                    break;
                }
            }

            return ringBuffer.getCursor();
        }

        @Override
        public void checkForAlert() throws AlertException
        {
            if (alerted)
            {
                alerted = false;
                throw ALERT_EXCEPTION;
            }
        }

        @Override
        public void alert()
        {
            alerted = true;
            notifyConsumers();
        }

        @Override
        public void notifyConsumers()
        {
        }
    }
}
