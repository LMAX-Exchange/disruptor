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

/**
 * Blocking strategy that uses a lock and condition variable for {@link EventProcessor}s waiting on a barrier.
 *
 * <p>使用锁和条件变量的阻塞策略，用于在 barrier 上等待的{@link EventProcessor}。</p>

 *
 * <p>This strategy can be used when throughput and low-latency are not as important as CPU resource.
 *
 * <p>当吞吐量和低延迟不像 CPU 资源那么重要时，可以使用此策略。</p>
 */
public final class BlockingWaitStrategy implements WaitStrategy
{
    // 锁对象，注意这个是成员变量；
    private final Object mutex = new Object();

    @Override
    public long waitFor(final long sequence, final Sequence cursorSequence, final Sequence dependentSequence, final SequenceBarrier barrier)
        throws AlertException, InterruptedException
    {
        long availableSequence;
        // 如果最近发布消息的 sequence 小于要求的 sequence 值，则必须等待
        if (cursorSequence.get() < sequence)
        {
            // 加锁
            synchronized (mutex)
            {
                // 循环重读
                while (cursorSequence.get() < sequence)
                {
                    // 校验中断
                    barrier.checkAlert();
                    // 自我阻塞并释放锁；
                    mutex.wait();
                    // 等到被唤醒之后，会重新竞争锁，然后才会重复 while
                }
            }
        }

        // 否则，说明 sequence 对应的消息已经发布了
        // 那么就需要判断依赖的 sequence 是否满足条件，即上游的消费者是否已经消费到了 sequence
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            // 校验中断
            barrier.checkAlert();
            // 明确当前线程处于自旋等待状态，便于底层进行性能优化
            Thread.onSpinWait();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        synchronized (mutex)
        {
            mutex.notifyAll();
        }
    }

    @Override
    public String toString()
    {
        return "BlockingWaitStrategy{" +
            "mutex=" + mutex +
            '}';
    }
}
