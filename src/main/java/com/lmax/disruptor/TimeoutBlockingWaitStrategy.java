package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TimeoutBlockingWaitStrategy implements WaitStrategy
{
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();
    private final long timeoutInNanos;

    public TimeoutBlockingWaitStrategy(final long timeout, final TimeUnit units)
    {
        timeoutInNanos = units.toNanos(timeout);
    }

    @Override
    public long waitFor(
        final long sequence,
        final Sequence cursorSequence,
        final Sequence dependentSequence,
        final SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException
    {
        long nanos = timeoutInNanos;

        long availableSequence;
        if (cursorSequence.get() < sequence)
        {
            lock.lock();
            try
            {
                while (cursorSequence.get() < sequence)
                {
                    barrier.checkAlert();
                    nanos = processorNotifyCondition.awaitNanos(nanos);
                    if (nanos <= 0)
                    {
                        throw TimeoutException.INSTANCE;
                    }
                }
            }
            finally
            {
                lock.unlock();
            }
        }

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        lock.lock();
        try
        {
            processorNotifyCondition.signalAll();
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public String toString()
    {
        return "TimeoutBlockingWaitStrategy{" +
            "processorNotifyCondition=" + processorNotifyCondition +
            '}';
    }
}
