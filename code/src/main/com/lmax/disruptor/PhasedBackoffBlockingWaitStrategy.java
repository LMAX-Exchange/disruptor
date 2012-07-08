/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 *
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class PhasedBackoffBlockingWaitStrategy implements WaitStrategy
{
    private static final int SPIN_TRIES = 1000000;
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();
    private volatile int numWaiters = 0;
    private long spinTimeout;
    private long yieldTimeout;

    public PhasedBackoffBlockingWaitStrategy(long spinTimeoutMillis, long yieldTimeoutMillis)
    {
        this.spinTimeout = TimeUnit.MILLISECONDS.toNanos(spinTimeoutMillis);
        this.yieldTimeout = TimeUnit.MILLISECONDS.toNanos(yieldTimeoutMillis);
    }
    
    @Override
    public long waitFor(final long sequence, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        return waitFor(sequence, dependentSequence, barrier, Long.MAX_VALUE, TimeUnit.SECONDS);
    }

    @Override
    public long waitFor(final long sequence,
                        final Sequence dependentSequence,
                        final SequenceBarrier barrier,
                        final long timeout,
                        final TimeUnit sourceUnit)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        long startTime = 0;
        int counter = SPIN_TRIES;
        
        do
        {
            if ((availableSequence = dependentSequence.get()) >= sequence)
            {
                return availableSequence;
            }
            
            if (--counter == 0)
            {
                if (0 == startTime)
                {
                    startTime = System.nanoTime();
                }
                else
                {
                    long timeDelta = System.nanoTime() - startTime;
                    if (timeDelta > yieldTimeout)
                    {
                        return waitOnLock(sequence, dependentSequence, barrier, timeout, sourceUnit);
                    }
                    else if (timeDelta > spinTimeout)
                    {
                        Thread.yield();
                    }
                }
                counter = SPIN_TRIES;
            }
        }
        while (true);
    }

    private long waitOnLock(final long sequence,
                            final Sequence dependentSequence,
                            final SequenceBarrier barrier,
                            final long timeout,
                            final TimeUnit sourceUnit)
            throws AlertException, InterruptedException
    {
        long availableSequence;
        lock.lock();
        try
        {
            ++numWaiters;
            while ((availableSequence = dependentSequence.get()) < sequence)
            {
                barrier.checkAlert();
                processorNotifyCondition.await(timeout, sourceUnit);
            }
            
            return availableSequence;
        }
        finally
        {
            --numWaiters;
            lock.unlock();
        }
    }

    @Override
    public void signalAllWhenBlocking()
    {
        if (0 != numWaiters)
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
    }
}
