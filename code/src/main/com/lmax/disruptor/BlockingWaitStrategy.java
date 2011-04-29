package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking strategy that uses locks and a condition variable for
 * {@link EventConsumer}s waiting on a barrier.
 *
 * This strategy should be used when performance and low-latency are not as important
 * as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    /** Pre-allocated exception to avoid garbage generation */
    public static final AlertException ALERT_EXCEPTION = new AlertException();

    private final RingBuffer ringBuffer;
    private final Lock lock = new ReentrantLock();
    private final Condition consumerNotifyCondition = lock.newCondition();

    private volatile boolean alerted = false;

    public BlockingWaitStrategy(final RingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public long waitFor(final long sequence)
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
    public long waitFor(final long sequence, final long timeout, final TimeUnit units)
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
        consumerNotifyCondition.signalAll();
        lock.unlock();
    }
}
