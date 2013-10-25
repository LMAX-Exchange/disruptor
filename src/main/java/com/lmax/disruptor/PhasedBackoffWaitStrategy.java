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
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>Phased wait strategy for waiting {@link EventProcessor}s on a barrier.</p>
 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 * Spins, then yields, then blocks on the configured BlockingStrategy.</p>
 */
public final class PhasedBackoffWaitStrategy implements WaitStrategy
{
    private static final int SPIN_TRIES = 10000;
    private final long spinTimeoutNanos;
    private final long yieldTimeoutNanos;
    private final BlockingStrategy lockingStrategy;

    public PhasedBackoffWaitStrategy(long spinTimeoutMillis,
                                     long yieldTimeoutMillis,
                                     TimeUnit units,
                                     BlockingStrategy lockingStrategy)
    {
        this.spinTimeoutNanos = units.toNanos(spinTimeoutMillis);
        this.yieldTimeoutNanos = spinTimeoutNanos + units.toNanos(yieldTimeoutMillis);
        this.lockingStrategy = lockingStrategy;
    }

    /**
     * Block with wait/notifyAll semantics
     */
    public static PhasedBackoffWaitStrategy withLock(long spinTimeoutMillis,
                                                     long yieldTimeoutMillis,
                                                     TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(spinTimeoutMillis, yieldTimeoutMillis,
                                             units, new LockBlockingStrategy());
    }

    /**
     * Block by sleeping in a loop
     */
    public static PhasedBackoffWaitStrategy withSleep(long spinTimeoutMillis,
                                                      long yieldTimeoutMillis,
                                                      TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(spinTimeoutMillis, yieldTimeoutMillis,
                                             units, new SleepBlockingStrategy());
    }

    @Override
    public long waitFor(long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
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

            if (0 == --counter)
            {
                if (0 == startTime)
                {
                    startTime = System.nanoTime();
                }
                else
                {
                    long timeDelta = System.nanoTime() - startTime;
                    if (timeDelta > yieldTimeoutNanos)
                    {
                        return lockingStrategy.waitOnLock(sequence, cursor, dependentSequence, barrier);
                    }
                    else if (timeDelta > spinTimeoutNanos)
                    {
                        Thread.yield();
                    }
                }
                counter = SPIN_TRIES;
            }
        }
        while (true);
    }

    @Override
    public void signalAllWhenBlocking()
    {
        lockingStrategy.signalAllWhenBlocking();
    }

    private interface BlockingStrategy
    {
        long waitOnLock(long sequence,
                        Sequence cursorSequence,
                        Sequence dependentSequence, SequenceBarrier barrier)
                throws AlertException, InterruptedException;

        void signalAllWhenBlocking();
    }

    private static class LockBlockingStrategy implements BlockingStrategy
    {
        private final Lock lock = new ReentrantLock();
        private final Condition processorNotifyCondition = lock.newCondition();

        @Override
        public long waitOnLock(long sequence,
                               Sequence cursorSequence,
                               Sequence dependentSequence,
                               SequenceBarrier barrier) throws AlertException, InterruptedException
        {
            long availableSequence;
            lock.lock();
            try
            {
                while ((availableSequence = cursorSequence.get()) < sequence)
                {
                    barrier.checkAlert();
                    processorNotifyCondition.await();
                }
            }
            finally
            {
                lock.unlock();
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
    }

    private static class SleepBlockingStrategy implements BlockingStrategy
    {
        public long waitOnLock(final long sequence,
                                Sequence cursorSequence,
                                final Sequence dependentSequence, final SequenceBarrier barrier)
                throws AlertException, InterruptedException
        {
            long availableSequence;

            while ((availableSequence = dependentSequence.get()) < sequence)
            {
                LockSupport.parkNanos(1);
            }

            return availableSequence;
        }

        @Override
        public void signalAllWhenBlocking()
        {
        }
    }
}
