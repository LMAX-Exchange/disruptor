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

import static com.lmax.disruptor.util.Util.getMinimumSequence;

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 *
 * This strategy can be used when throughput and low-latency are not as important as CPU resource.
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    private final Lock lock = new ReentrantLock();
    private final Condition processorNotifyCondition = lock.newCondition();
    private volatile int numWaiters = 0;

    @Override
    public long waitFor(final long sequence, final Sequence cursor, final Sequence[] dependents, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        if ((availableSequence = cursor.get()) < sequence)
        {
            lock.lock();
            try
            {
                ++numWaiters;
                while ((availableSequence = cursor.get()) < sequence)
                {
                    barrier.checkAlert();
                    processorNotifyCondition.await();
                }
            }
            finally
            {
                --numWaiters;
                lock.unlock();
            }
        }

        if (0 != dependents.length)
        {
            while ((availableSequence = getMinimumSequence(dependents)) < sequence)
            {
                barrier.checkAlert();
            }
        }

        return availableSequence;
    }

    @Override
    public long waitFor(final long sequence, final Sequence cursor, final Sequence[] dependents, final SequenceBarrier barrier,
                        final long timeout, final TimeUnit sourceUnit)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        if ((availableSequence = cursor.get()) < sequence)
        {
            lock.lock();
            try
            {
                ++numWaiters;
                while ((availableSequence = cursor.get()) < sequence)
                {
                    barrier.checkAlert();

                    if (!processorNotifyCondition.await(timeout, sourceUnit))
                    {
                        break;
                    }
                }
            }
            finally
            {
                --numWaiters;
                lock.unlock();
            }
        }

        if (0 != dependents.length)
        {
            while ((availableSequence = getMinimumSequence(dependents)) < sequence)
            {
                barrier.checkAlert();
            }
        }

        return availableSequence;
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
