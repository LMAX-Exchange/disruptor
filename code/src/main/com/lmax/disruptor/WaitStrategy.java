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
import static com.lmax.disruptor.Util.getMinimumSequence;

/**
 * Strategy employed for making {@link EventProcessor}s wait on a {@link RingBuffer}.
 */
public interface WaitStrategy
{
    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer}
     *
     * @param eventProcessors further back the chain that must advance first
     * @param ringBuffer on which to wait.
     * @param barrier the processor is waiting on.
     * @param sequence to be waited on.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(EventProcessor[] eventProcessors, RingBuffer ringBuffer, EventProcessorBarrier barrier,  long sequence)
        throws AlertException, InterruptedException;

    /**
     * Wait for the given sequence to be available for consumption in a {@link RingBuffer} with a timeout specified.
     *
     * @param eventProcessors further back the chain that must advance first
     * @param ringBuffer on which to wait.
     * @param barrier the processor is waiting on.
     * @param sequence to be waited on.
     * @param timeout value to abort after.
     * @param units of the timeout value.
     * @return the sequence that is available which may be greater than the requested sequence.
     * @throws AlertException if the status of the Disruptor has changed.
     * @throws InterruptedException if the thread is interrupted.
     */
    long waitFor(EventProcessor[] eventProcessors, RingBuffer ringBuffer, EventProcessorBarrier barrier, long sequence, long timeout, TimeUnit units)
        throws AlertException, InterruptedException;

    /**
     * Signal those waiting that the {@link RingBuffer} cursor has advanced.
     */
    void signalAll();

    /**
     * Strategy options which are available to those waiting on a {@link RingBuffer}
     */
    enum Option
    {
        /** This strategy uses a condition variable inside a lock to block the event processor
         * which saves CPU resource as the expense of lock contention. */
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

        /** This strategy call spins in a loop as a waiting strategy which is lowest and most consistent latency but ties up a CPU */
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
     * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
     *
     * This strategy should be used when performance and low-latency are not as important as CPU resource.
     */
    static final class BlockingStrategy implements WaitStrategy
    {
        private final Lock lock = new ReentrantLock();
        private final Condition processorNotifyCondition = lock.newCondition();

        @Override
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;
            if ((availableSequence = ringBuffer.getCursor()) < sequence)
            {
                lock.lock();
                try
                {
                    while ((availableSequence = ringBuffer.getCursor()) < sequence)
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
                    lock.unlock();
                }
            }

            if (0 != eventProcessors.length)
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
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
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            long availableSequence;
            if ((availableSequence = ringBuffer.getCursor()) < sequence)
            {
                lock.lock();
                try
                {
                    while ((availableSequence = ringBuffer.getCursor()) < sequence)
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
                    lock.unlock();
                }
            }

            if (0 != eventProcessors.length)
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
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
        public void signalAll()
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

    /**
     * Yielding strategy that uses a Thread.yield() for {@link EventProcessor}s waiting on a barrier.
     *
     * This strategy is a good compromise between performance and CPU resource.
     */
    static final class YieldingStrategy implements WaitStrategy
    {
        @Override
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;

            if (0 == eventProcessors.length)
            {
                while ((availableSequence = ringBuffer.getCursor()) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }
                    Thread.yield();
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }
                    Thread.yield();
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();
            long availableSequence;

            if (0 == eventProcessors.length)
            {
                while ((availableSequence = ringBuffer.getCursor()) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }

                    Thread.yield();
                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }

                    Thread.yield();
                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public void signalAll()
        {
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
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier, final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence;

            if (0 == eventProcessors.length)
            {
                while ((availableSequence = ringBuffer.getCursor()) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
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
        public long waitFor(final EventProcessor[] eventProcessors, final RingBuffer ringBuffer, final EventProcessorBarrier barrier,
                            final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
            final long currentTime = System.currentTimeMillis();
            long availableSequence;

            if (0 == eventProcessors.length)
            {
                while ((availableSequence = ringBuffer.getCursor()) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }
            else
            {
                while ((availableSequence = getMinimumSequence(eventProcessors)) < sequence)
                {
                    if (barrier.isAlerted())
                    {
                        throw ALERT_EXCEPTION;
                    }

                    if (timeoutMs < (System.currentTimeMillis() - currentTime))
                    {
                        break;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public void signalAll()
        {
        }
    }
}
