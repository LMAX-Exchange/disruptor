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

import static com.lmax.disruptor.AlertException.ALERT_EXCEPTION;
import static com.lmax.disruptor.util.Util.getMinimumSequence;

/**
 * Strategy employed for making {@link EventProcessor}s wait on a cursor {@link Sequence}.
 */
public interface WaitStrategy
{
    /**
     * Wait for the given sequence to be available
     *
     * @param dependents further back the chain that must advance first
     * @param cursor on which to wait.
     * @param barrier the processor is waiting on.
     * @param sequence to be waited on.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(Sequence[] dependents, Sequence cursor, SequenceBarrier barrier,  long sequence)
        throws AlertException, InterruptedException;

    /**
     * Wait for the given sequence to be available with a timeout specified.
     *
     * @param dependents further back the chain that must advance first
     * @param cursor on which to wait.
     * @param barrier the processor is waiting on.
     * @param sequence to be waited on.
     * @param timeout value to abort after.
     * @param units of the timeout value.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(Sequence[] dependents, Sequence cursor, SequenceBarrier barrier, long sequence, long timeout, TimeUnit units)
        throws AlertException, InterruptedException;

    /**
     * Signal those waiting that the cursor has advanced.
     */
    void signalAllWhenBlocking();

    /**
     * Strategy options which are available to those waiting on a sequence
     */
    enum Option
    {
        /**
         * This strategy uses a condition variable inside a lock to block the event processor
         * which saves CPU resource as the expense of lock contention.
         */
        BLOCKING
        {
            @Override
            public WaitStrategy newInstance()
            {
                return new BlockingStrategy();
            }
        },

        /**
         * This strategy uses a progressive back off strategy by first spinning, then yielding, then sleeping for 1ms periods.
         * This is a good strategy for burst traffic then quiet periods when latency is not critical.
         */
        SLEEPING
        {
            @Override
            public SleepingStrategy newInstance()
            {
                return new SleepingStrategy();
            }
        },

        /**
         * This strategy calls Thread.yield() in a loop as a waiting strategy which reduces contention
         * at the expense of CPU resource.
         */
        YIELDING
        {
            @Override
            public WaitStrategy newInstance()
            {
                return new YieldingStrategy();
            }
        },

        /**
         * This strategy call spins in a loop as a waiting strategy which is lowest
         * and most consistent latency but ties up a CPU
         */
        BUSY_SPIN
        {
            @Override
            public WaitStrategy newInstance()
            {
                return new BusySpinStrategy();
            }
        };

        /**
         * Used by the {@link Sequencer} as a polymorphic constructor.
         *
         * @return a new instance of the WaitStrategy
         */
        abstract WaitStrategy newInstance();
    }

    /**
     * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
     *
     * This strategy should be used when performance and low-latency are not as important as CPU resource.
     */
    static final class BlockingStrategy implements WaitStrategy
    {
        private final Lock lock = new ReentrantLock();
        private final Condition processorNotifyCondition = lock.newCondition();
        private volatile int numWaiters = 0;

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier, final long sequence)
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
                        if (barrier.isAlerted())
                        {
                            throw ALERT_EXCEPTION;
                        }
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
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
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
                        if (barrier.isAlerted())
                        {
                            throw ALERT_EXCEPTION;
                        }

                        if (!processorNotifyCondition.await(timeout, units))
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
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }
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

    /**
     * Sleeping strategy that initially spins, then uses a Thread.yield(), and eventually sleeping for 1ms
     * while the {@link EventProcessor}s are waiting on a barrier.
     *
     * This strategy is a good compromise between performance and CPU resource. Latency spikes can occur after quiet periods.
     */
    static final class SleepingStrategy implements WaitStrategy
    {
        private static final int RETRIES = 200;

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;

            int counter = RETRIES;
            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();
            long availableSequence;

            int counter = RETRIES;
            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public void signalAllWhenBlocking()
        {
        }

        private int applyWaitMethod(final SequenceBarrier barrier, int counter)
            throws AlertException
        {
            if (barrier.isAlerted())
            {
                throw ALERT_EXCEPTION;
            }

            if (counter > 100)
            {
                --counter;
            }
            else if (counter > 0)
            {
                --counter;
                Thread.yield();
            }
            else
            {
                try
                {
                    Thread.sleep(1L);
                }
                catch (InterruptedException ex)
                {
                    // do nothing
                }
            }

            return counter;
        }
    }

    /**
     * Yielding strategy that uses a Thread.yield() for {@link EventProcessor}s waiting on a barrier
     * after an initially spinning.
     *
     * This strategy is a good compromise between performance and CPU resource without significant latency spikes.
     */
    static final class YieldingStrategy implements WaitStrategy
    {
        private static final int SPIN_TRIES = 100;

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;

            int counter = SPIN_TRIES;
            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();
            long availableSequence;

            int counter = SPIN_TRIES;
            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    counter = applyWaitMethod(barrier, counter);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public void signalAllWhenBlocking()
        {
        }

        private int applyWaitMethod(final SequenceBarrier barrier, int counter)
            throws AlertException
        {
            if (barrier.isAlerted())
            {
                throw ALERT_EXCEPTION;
            }

            if (0 == counter)
            {
                Thread.yield();
            }
            else
            {
                --counter;
            }

            return counter;
        }
    }

    /**
     * Busy Spin strategy that uses a busy spin loop for {@link EventProcessor}s waiting on a barrier.
     *
     * This strategy will use CPU resource to avoid syscalls which can introduce latency jitter.  It is best
     * used when threads can be bound to specific CPU cores.
     */
    static final class BusySpinStrategy implements WaitStrategy
    {
        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;

            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    applyWaitMethod(barrier);
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    applyWaitMethod(barrier);
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final Sequence[] dependents, final Sequence cursor, final SequenceBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();
            long availableSequence;

            if (0 == dependents.length)
            {
                while ((availableSequence = cursor.get()) < sequence)
                {
                    applyWaitMethod(barrier);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(dependents)) < sequence)
                {
                    applyWaitMethod(barrier);

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public void signalAllWhenBlocking()
        {
        }

        private void applyWaitMethod(final SequenceBarrier barrier)
            throws AlertException
        {
            if (barrier.isAlerted())
            {
                throw ALERT_EXCEPTION;
            }
        }
    }
}
