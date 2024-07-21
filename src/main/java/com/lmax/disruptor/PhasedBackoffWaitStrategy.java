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

/**
 * Phased wait strategy for waiting {@link EventProcessor}s on a barrier.
 *
 * <p>用于等待{@link EventProcessor}在屏障上的分阶段等待策略。</p>
 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 * Spins, then yields, then waits using the configured fallback WaitStrategy.
 *
 * <p>当吞吐量和低延迟不像CPU资源那么重要时，可以使用此策略。
 * 旋转，然后产生，然后使用配置的回退WaitStrategy等待。</p>
 */
public final class PhasedBackoffWaitStrategy implements WaitStrategy
{
    private static final int SPIN_TRIES = 10000;
    private final long spinTimeoutNanos;
    private final long yieldTimeoutNanos;
    // 当前 PhasedBackoffWaitStrategy 失效后的降级策略
    private final WaitStrategy fallbackStrategy;

    /**
     *
     * @param spinTimeout The maximum time in to busy spin for.
     * @param yieldTimeout The maximum time in to yield for.
     * @param units Time units used for the timeout values.
     * @param fallbackStrategy After spinning + yielding, the strategy to fall back to
     */
    public PhasedBackoffWaitStrategy(
        final long spinTimeout,
        final long yieldTimeout,
        final TimeUnit units,
        final WaitStrategy fallbackStrategy)
    {
        this.spinTimeoutNanos = units.toNanos(spinTimeout);
        this.yieldTimeoutNanos = spinTimeoutNanos + units.toNanos(yieldTimeout);
        this.fallbackStrategy = fallbackStrategy;
    }

    /**
     * Construct {@link PhasedBackoffWaitStrategy} with fallback to {@link BlockingWaitStrategy}
     *
     * <p>构造具有降级到{@link BlockingWaitStrategy}的{@link PhasedBackoffWaitStrategy}</p>
     *
     * @param spinTimeout The maximum time in to busy spin for.
     * @param yieldTimeout The maximum time in to yield for.
     * @param units Time units used for the timeout values.
     * @return The constructed wait strategy.
     */
    public static PhasedBackoffWaitStrategy withLock(
        final long spinTimeout,
        final long yieldTimeout,
        final TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new BlockingWaitStrategy());
    }

    /**
     * Construct {@link PhasedBackoffWaitStrategy} with fallback to {@link LiteBlockingWaitStrategy}
     *
     * <p>构造具有降级到{@link LiteBlockingWaitStrategy}的{@link PhasedBackoffWaitStrategy}</p>
     *
     * @param spinTimeout The maximum time in to busy spin for.
     * @param yieldTimeout The maximum time in to yield for.
     * @param units Time units used for the timeout values.
     * @return The constructed wait strategy.
     */
    public static PhasedBackoffWaitStrategy withLiteLock(
        final long spinTimeout,
        final long yieldTimeout,
        final TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new LiteBlockingWaitStrategy());
    }

    /**
     * Construct {@link PhasedBackoffWaitStrategy} with fallback to {@link SleepingWaitStrategy}
     *
     * @param spinTimeout The maximum time in to busy spin for.
     * @param yieldTimeout The maximum time in to yield for.
     * @param units Time units used for the timeout values.
     * @return The constructed wait strategy.
     */
    public static PhasedBackoffWaitStrategy withSleep(
        final long spinTimeout,
        final long yieldTimeout,
        final TimeUnit units)
    {
        return new PhasedBackoffWaitStrategy(
            spinTimeout, yieldTimeout,
            units, new SleepingWaitStrategy(0));
    }

    @Override
    public long waitFor(final long sequence, final Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException
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

            // 如果自旋次数到了 0
            if (0 == --counter)
            {
                // 记录开始时间
                if (0 == startTime)
                {
                    startTime = System.nanoTime();
                }
                else
                {
                    long timeDelta = System.nanoTime() - startTime;
                    // 如果时间间隔超过了 yieldTimeoutNanos，则调用降级后的 Strategy 来等待
                    if (timeDelta > yieldTimeoutNanos)
                    {
                        return fallbackStrategy.waitFor(sequence, cursor, dependentSequence, barrier);
                    }
                    // 否则触发 yield
                    else if (timeDelta > spinTimeoutNanos)
                    {
                        Thread.yield();
                    }
                }
                // 重置自旋的 counter
                counter = SPIN_TRIES;
            }
        }
        while (true);
    }

    @Override
    public void signalAllWhenBlocking()
    {
        // 调用降级后的 Strategy 来唤醒所有等待的线程
        fallbackStrategy.signalAllWhenBlocking();
    }
}
