package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.LockSupport;

/**
 * Parking strategy that spins then parks the thread. The goal of this srategy
 * is to avoid the object creation that occurs when using subclasses of
 * {@link AbstractQueuedSynchronizer}. This strategy will also only work with a
 * single processing thread.
 * 
 * <p>
 * This strategy can be used when throughput and low-latency are not as
 * important as CPU resource.
 */
public class ParkingWaitStrategy implements WaitStrategy
{
    private final AtomicReference<Thread> parkedThread = new AtomicReference<>(null);
    private final int spinTries;

    public ParkingWaitStrategy()
    {
        this(100);
    }

    public ParkingWaitStrategy(int spinTries)
    {
        this.spinTries = spinTries;
    }

    @Override
    public long waitFor(final long sequence, Sequence cursor, final Sequence dependentSequence,
                        final SequenceBarrier barrier) throws AlertException, InterruptedException
    {
        long availableSequence;
        int counter = spinTries;

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            counter = applyWaitMethod(barrier, counter);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        final Thread t = parkedThread.getAndSet(null);

        if (null != t)
            LockSupport.unpark(t);
    }

    private int applyWaitMethod(final SequenceBarrier barrier, int counter) throws AlertException
    {
        barrier.checkAlert();
        if (0 == counter)
        {
            final Thread t = Thread.currentThread();

            parkedThread.getAndSet(t);
            LockSupport.park(t);
        }
        else
        {
            --counter;
        }

        return counter;
    }
}