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

import com.lmax.disruptor.util.Util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;
// 数据填充用的对象，避免伪共享
abstract class SingleProducerSequencerPad extends AbstractSequencer
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    SingleProducerSequencerPad(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
}

// 定义了 nextValue 和 cachedValue 两个字段
abstract class SingleProducerSequencerFields extends SingleProducerSequencerPad
{
    SingleProducerSequencerFields(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * Set to -1 as sequence starting point
     */
    long nextValue = Sequence.INITIAL_VALUE;
    long cachedValue = Sequence.INITIAL_VALUE;
}

/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Not safe for use from multiple threads as it does not implement any barriers.
 *
 * <p>用于声明序列以访问数据结构的协调器，同时跟踪依赖的{@link Sequence}。
 * 由于它不实现任何屏障，因此不适合从多个线程中使用。</p>
 *
 * <p>* Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#publish(long)} is made.
 *
 * <p>关于{@link Sequencer#getCursor()}的说明：使用此顺序器时，游标值在调用{@link Sequencer#publish(long)}之后更新。</p>
 */

public final class SingleProducerSequencer extends SingleProducerSequencerFields
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * <p>使用指定的等待策略和缓冲区大小构造一个Sequencer。</p>
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(requiredCapacity, false);
    }

    private boolean hasAvailableCapacity(final int requiredCapacity, final boolean doStore)
    {
        // 获取下一个可发布的 Sequence
        long nextValue = this.nextValue;
        // TODO 将整个数组看作是滑动窗口，则假设 nextValue 是窗口的右边界，它在向右滑动 requiredCapacity 个单位后的数组左边界对应的 sequence 值
        // 它代表了数组能维持的最旧的数据，如果消费者的最小序号小于这个值，说明此时无法直接滑动 requiredCapacity 个单位，即 unavailable
        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        // 获取消费者的最小序号
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence // 说明生产者发布新事件后，会超过当前消费者的最小序列号
                || cachedGatingSequence > nextValue) // 说明消费者的序号已经超过了生产者的序号，这通常不会发生
        {
            // 如果需要更新 cursor 的值，那么就更新；即这次的 requiredCapacity 会被记录下来
            if (doStore)
            {
                cursor.setVolatile(nextValue);  // StoreLoad fence
            }

            // 获取消费者的最小序号，更新 cachedValue
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            this.cachedValue = minSequence;

            // 再次判断
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#next()
     */
    @Override
    public long next()
    {
        return next(1);
    }

    /**
     * @see Sequencer#next(int)
     */
    @Override
    public long next(final int n)
    {
        // 验证该 singleProducerSequencer 始终只有一个线程 produce event
        assert sameThread() : "Accessed by two threads - use ProducerType.MULTI!";

        // 验证数据正确性
        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        // 取到 nextValue，默认初始值为 -1
        long nextValue = this.nextValue;

        // 计算得到下一个 Sequence，即需要返回的 sequence 值
        long nextSequence = nextValue + n;
        // 通过计算 wrapPoint，判断容量是否足够
        // wrapPoint 完全可能是负数（特别是一开始的时候）
        long wrapPoint = nextSequence - bufferSize;
        // 取到消费者的最小序号，这是一个缓存值；因此可能会偏小
        long cachedGatingSequence = this.cachedValue;

        if (wrapPoint > cachedGatingSequence // TODO 这部分的理解要这样看：wrapPoint > cachedGatingSequence
                                             // 即 nextSequence - bufferSize > cachedGatingSequence
                                             // 即说明两个游标之间的差值大于数组容量了已经，画个一维坐标就能理解了；就表示生产者在覆盖 event 的时候会覆盖到消费者尚未消费的 event
                || cachedGatingSequence > nextValue)
        {
            // 更新 cursor 的值，这里会增加一个 StoreLoad fence
            cursor.setVolatile(nextValue);  // StoreLoad fence

            // 使用 minSequence 记录消费者的最小序号
            long minSequence;
            // 持续等待并感知消费者的进度，直到剩余容量足够
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }

            this.cachedValue = minSequence;
        }

        this.nextValue = nextSequence;

        return nextSequence;
    }

    /**
     * @see Sequencer#tryNext()
     */
    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    /**
     * @see Sequencer#tryNext(int)
     */
    @Override
    public long tryNext(final int n) throws InsufficientCapacityException
    {
        if (n < 1)
        {
            throw new IllegalArgumentException("n must be > 0");
        }

        // 注意，这里设置了 dbStore 为 true，即如果容量不够了，那么会更新 cursor 的值；并且如果容量不够，会抛异常
        if (!hasAvailableCapacity(n, true))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = this.nextValue += n;

        return nextSequence;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        // 取到 nextValue，默认初始值为 -1；即最近一个已发布的 event 的序号
        long nextValue = this.nextValue;
        // 获取消费者消费序号中最小的一个
        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        // (produced - consumed) 代表不能被释放的容量，因为有消费者还没有消费中间的消息
        // getBufferSize() - (produced - consumed) 代表剩余的可用容量
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(final long sequence)
    {
        // 直接设置 nextValue 为 sequence，一般在初始化时使用
        this.nextValue = sequence;
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence)
    {
        // 设置 cursor 的值为 sequence，即代表该位置的 event 被发布了
        cursor.set(sequence);
        // 通知等待的消费者（具体依赖 waitStrategy）
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(final long lo, final long hi)
    {
        publish(hi);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(final long sequence)
    {
        // 获取到 cursor 的值，即代表最近发布的 event 的序号
        final long currentSequence = cursor.get();
        // 假设 ringBuffer 是一个滑动窗口，则 currentSequence 是窗口的右边界，currentSequence - bufferSize 是窗口的左边界
        // 因此下面是校验 sequence 是否在窗口内，即是否在这两个边界之间
        return sequence <= currentSequence && sequence > currentSequence - bufferSize;
    }

    @Override
    public long getHighestPublishedSequence(final long lowerBound, final long availableSequence)
    {
        // 根据接口定义，入参 availableSequence > lowerBound
        // 因此针对 single 单线程的 sequencer，这里直接返回 availableSequence 即可
        return availableSequence;
    }

    @Override
    public String toString()
    {
        return "SingleProducerSequencer{" +
                "bufferSize=" + bufferSize +
                ", waitStrategy=" + waitStrategy +
                ", cursor=" + cursor +
                ", gatingSequences=" + Arrays.toString(gatingSequences) +
                '}';
    }

    private boolean sameThread()
    {
        return ProducerThreadAssertion.isSameThreadProducingTo(this);
    }

    /**
     * Only used when assertions are enabled.
     *
     * <p>仅在启用断言时使用。</p>
     */
    private static class ProducerThreadAssertion
    {
        /**
         * Tracks the threads publishing to {@code SingleProducerSequencer}s to identify if more than one
         * thread accesses any {@code SingleProducerSequencer}.
         * I.e. it helps developers detect early if they use the wrong
         * {@link com.lmax.disruptor.dsl.ProducerType}.
         *
         * <p>跟踪发布到{@code SingleProducerSequencer}的线程，以确定是否有多个线程访问任何{@code SingleProducerSequencer}。
         * 例如，它有助于开发人员尽早检测到是否使用了错误的{@link com.lmax.disruptor.dsl.ProducerType}。</p>
         */
        private static final Map<SingleProducerSequencer, Thread> PRODUCERS = new HashMap<>();

        public static boolean isSameThreadProducingTo(final SingleProducerSequencer singleProducerSequencer)
        {
            synchronized (PRODUCERS)
            {
                final Thread currentThread = Thread.currentThread();
                // 如果全局 map 中不存在 key=sequencer，则将当前线程作为 value 存入 map，表示第一次访问
                if (!PRODUCERS.containsKey(singleProducerSequencer))
                {
                    PRODUCERS.put(singleProducerSequencer, currentThread);
                }
                // 验证当前线程是否和 map 中的 value 相同
                return PRODUCERS.get(singleProducerSequencer).equals(currentThread);
            }
        }
    }
}
