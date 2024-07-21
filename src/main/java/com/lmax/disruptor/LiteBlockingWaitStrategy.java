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

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Variation of the {@link BlockingWaitStrategy} that attempts to elide conditional wake-ups when
 * the lock is uncontended.  Shows performance improvements on microbenchmarks.  However this
 * wait strategy should be considered experimental as I have not full proved the correctness of
 * the lock elision code.
 *
 * <p>尝试省略条件唤醒的{@link BlockingWaitStrategy}变体，当锁没有争用时。
 * 在微基准测试中显示性能提升。
 * 但是，应该将此等待策略视为实验性的，因为我尚未完全证明锁省略代码的正确性。</p>
 */
public final class LiteBlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();
    // 对比 BlockingWaitStrategy，增加了 signalNeeded 标志位
    private final AtomicBoolean signalNeeded = new AtomicBoolean(false);

    @Override
    public long waitFor(final long sequence, final Sequence cursorSequence, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        // 当 publisher 的 sequence 小于指定 sequence 时，阻塞等待
        if (cursorSequence.get() < sequence)
        {
            synchronized (mutex)
            {
                do
                {
                    // 将 signalNeeded 设置为 true，表示需要唤醒
                    // TODO 不确定这一行能不能被放到 break 后面？或者 wait 前面？
                    signalNeeded.getAndSet(true);

                    if (cursorSequence.get() >= sequence)
                    {
                        break;
                    }

                    barrier.checkAlert();
                    mutex.wait();
                }
                while (cursorSequence.get() < sequence);
            }
        }
        // 当依赖的消费者的 sequence 都小于指定 sequence 时，自旋等待
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
            Thread.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        // 可能能够规避不需要的唤醒
        if (signalNeeded.getAndSet(false))
        {
            synchronized (mutex)
            {
                mutex.notifyAll();
            }
        }
    }

    @Override
    public String toString()
    {
        return "LiteBlockingWaitStrategy{" +
            "mutex=" + mutex +
            ", signalNeeded=" + signalNeeded +
            '}';
    }
}
