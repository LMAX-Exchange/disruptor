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

import java.util.concurrent.locks.LockSupport;

/**
 * Sleeping strategy that initially spins, then uses a Thread.yield(), and
 * eventually sleep (<code>LockSupport.parkNanos(n)</code>) for the minimum
 * number of nanos the OS and JVM will allow while the
 * {@link com.lmax.disruptor.EventProcessor}s are waiting on a barrier.
 *
 * <p>睡眠等待的策略，会先自旋，然后调用 Thread.yield，最后通过 LockSupport.parkNanos 来自短时我阻塞</p>
 *
 * <p>This strategy is a good compromise between performance and CPU resource.
 * Latency spikes can occur after quiet periods.  It will also reduce the impact
 * on the producing thread as it will not need signal any conditional variables
 * to wake up the event handling thread.
 *
 * <p>这种策略在性能和 CPU 资源之间取得了很好的平衡。
 * 在静默期之后可能会出现延迟峰值。
 * 它还将减少对生产线程的影响，因为它不需要信号任何条件变量来唤醒事件处理线程。</p>
 */
public final class SleepingWaitStrategy implements WaitStrategy
{
    private static final int SPIN_THRESHOLD = 100;
    private static final int DEFAULT_RETRIES = 200;
    private static final long DEFAULT_SLEEP = 100;

    private final int retries;
    private final long sleepTimeNs;

    /**
     * Provides a sleeping wait strategy with the default retry and sleep settings
     */
    public SleepingWaitStrategy()
    {
        this(DEFAULT_RETRIES, DEFAULT_SLEEP);
    }

    /**
     * @param retries How many times the strategy should retry before sleeping
     */
    public SleepingWaitStrategy(final int retries)
    {
        this(retries, DEFAULT_SLEEP);
    }

    /**
     * @param retries How many times the strategy should retry before sleeping
     * @param sleepTimeNs How long the strategy should sleep, in nanoseconds
     */
    public SleepingWaitStrategy(final int retries, final long sleepTimeNs)
    {
        // 如果 retries < SPIN_THRESHOLD，那么就不会进行自旋，而是直接 yield
        // 如果 retries < 0，那么就会直接 sleep
        this.retries = retries;
        this.sleepTimeNs = sleepTimeNs;
    }

    @Override
    public long waitFor(
        final long sequence, final Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException
    {
        long availableSequence;
        int counter = retries;

        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            counter = applyWaitMethod(barrier, counter);
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
    }

    private int applyWaitMethod(final SequenceBarrier barrier, final int counter)
        throws AlertException
    {
        barrier.checkAlert();

        // 优先自旋
        if (counter > SPIN_THRESHOLD)
        {
            return counter - 1;
        }
        // 如果 count < SPIN_THRESHOLD，那么就 yield
        else if (counter > 0)
        {
            Thread.yield();
            return counter - 1;
        }
        // 否则 count <= 0 则直接 park
        else
        {
            LockSupport.parkNanos(sleepTimeNs);
        }

        return counter;
    }
}
