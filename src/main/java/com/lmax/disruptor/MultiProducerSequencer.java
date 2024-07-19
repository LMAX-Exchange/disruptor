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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.
 * Suitable for use for sequencing across multiple publisher threads.
 *
 * <p>用于为访问数据结构声明序列并跟踪依赖{@link Sequence}的协调器。
 * 适用于跨多个发布者线程进行排序。</p>
 *
 * <p>Note on {@link Sequencer#getCursor()}:  With this sequencer the cursor value is updated after the call
 * to {@link Sequencer#next()}, to determine the highest available sequence that can be read, then
 * {@link Sequencer#getHighestPublishedSequence(long, long)} should be used.
 *
 * <p>关于{@link Sequencer#getCursor()}的说明：使用此顺序器后，调用{@link Sequencer#next()}后更新游标值，
 * 以确定可以读取的最高可用序列，然后应使用{@link Sequencer#getHighestPublishedSequence(long, long)}。</p>
 */
public final class MultiProducerSequencer extends AbstractSequencer
{
    // VarHandle 是 Java 9 引入的，用于替代 Java 8 中的 Unsafe 类
    private static final VarHandle AVAILABLE_ARRAY = MethodHandles.arrayElementVarHandle(int[].class);

    // 类似 SingleProducerSequencer 中的 cachedValue
    private final Sequence gatingSequenceCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    // availableBuffer tracks the state of each ringbuffer slot
    // see below for more details on the approach
    // availableBuffer 跟踪每个环形缓冲区插槽的状态
    private final int[] availableBuffer;
    // 索引掩码
    private final int indexMask;
    // 记录 bufferSize 为 2 的几次幂
    private final int indexShift;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize   the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
        // 拥有和 RingBuffer 相同的 size
        availableBuffer = new int[bufferSize];
        Arrays.fill(availableBuffer, -1);

        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
    }

    /**
     * @see Sequencer#hasAvailableCapacity(int)
     */
    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(gatingSequences, requiredCapacity, cursor.get());
    }

    private boolean hasAvailableCapacity(final Sequence[] gatingSequences, final int requiredCapacity, final long cursorValue)
    {
        // 这里直接取 cursor value，而不是像 SingleProducerSequencer 那样取 next value
        // 因此 cursor 能直接反应最近发布消息的 sequence 值
        long wrapPoint = (cursorValue + requiredCapacity) - bufferSize;
        // 取出缓存的消费者最小消费序列
        long cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint > cachedGatingSequence
                || cachedGatingSequence > cursorValue)
        {
            // 取得最新的消费者最小消费序列，并更新缓存
            long minSequence = Util.getMinimumSequence(gatingSequences, cursorValue);
            gatingSequenceCache.set(minSequence);

            // 再次比较 wrapPoint 和 minSequence
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * @see Sequencer#claim(long)
     */
    @Override
    public void claim(final long sequence)
    {
        // 对比 SingleProducerSequencer，这里没有 nextValue，而是直接设置 cursor
        cursor.set(sequence);
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
        if (n < 1 || n > bufferSize)
        {
            throw new IllegalArgumentException("n must be > 0 and < bufferSize");
        }

        // cas 更新 cursor
        // TODO cursor 会在 next 的时候就更新
        long current = cursor.getAndAdd(n);

        long nextSequence = current + n;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();

        if (wrapPoint > cachedGatingSequence
                || cachedGatingSequence > current)
        {
            long gatingSequence;
            while (wrapPoint > (gatingSequence = Util.getMinimumSequence(gatingSequences, current)))
            {
                LockSupport.parkNanos(1L); // TODO, should we spin based on the wait strategy?
            }

            gatingSequenceCache.set(gatingSequence);
        }

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

        long current;
        long next;

        // 尝试 cas 的持续更新 cursor；
        // 如果容量不够就抛异常
        do
        {
            current = cursor.get();
            next = current + n;

            if (!hasAvailableCapacity(gatingSequences, n, current))
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;
    }

    /**
     * @see Sequencer#remainingCapacity()
     */
    @Override
    public long remainingCapacity()
    {
        // TODO 为啥这里不更新 gatingSequenceCache？
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    /**
     * @see Sequencer#publish(long)
     */
    @Override
    public void publish(final long sequence)
    {
        // 设置 availableBuffer 中 sequence 对应的 index 为 available
        setAvailable(sequence);
        // 通知所有等待的线程
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * @see Sequencer#publish(long, long)
     */
    @Override
    public void publish(final long lo, final long hi)
    {
        // 设置 lo 到 hi 范围内的 availableBuffer 的 flag 值
        for (long l = lo; l <= hi; l++)
        {
            setAvailable(l);
        }
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * The below methods work on the availableBuffer flag.
     *
     * <p>下面的方法在 availableBuffer 标志上工作。</p>
     *
     * <p>The prime reason is to avoid a shared sequence object between publisher threads.
     * (Keeping single pointers tracking start and end would require coordination
     * between the threads).
     *
     * <p>主要原因是避免发布者线程之间共享序列对象。
     * （保持跟踪开始和结束的单个指针需要在线程之间协调）。</p>
     *
     * <p>--  Firstly we have the constraint that the delta between the cursor and minimum
     * gating sequence will never be larger than the buffer size (the code in
     * next/tryNext in the Sequence takes care of that).
     * -- Given that; take the sequence value and mask off the lower portion of the
     * sequence as the index into the buffer (indexMask). (aka modulo operator)
     * -- The upper portion of the sequence becomes the value to check for availability.
     * ie: it tells us how many times around the ring buffer we've been (aka division)
     * -- Because we can't wrap without the gating sequences moving forward (i.e. the
     * minimum gating sequence is effectively our last available position in the
     * buffer), when we have new data and successfully claimed a slot we can simply
     * write over the top.
     *
     * <p>-- 首先，我们有一个约束，即 cursor 和最小 gating sequence 之间的差值永远不会大于缓冲区大小（Sequence 中的 next/tryNext 代码会处理这个问题）。
     * -- 鉴于此；取入参 sequence 值并掩码掉 sequence 的较低部分作为缓冲区的索引（indexMask）。（又称模运算）
     * -- sequence 的较高部分成为可用性检查的值。即：它告诉我们环形缓冲区绕了多少圈（又称除法）
     * -- 因为如果没有 gating sequences 前进（消费），我们不能包装（即最小 gating sequence 实际上是缓冲区中的最后可用位置），
     * 所以当我们有新数据并成功声明一个插槽时，我们可以简单地覆盖顶部。</p>
     */
    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(
                calculateIndex(sequence), // 取模，得到的结果即为其在 availableBuffer 中的 index 索引（数组下标）
                calculateAvailabilityFlag(sequence)); // 除法，得到的结果即为 sequence 环绕的圈数
    }

    private void setAvailableBufferValue(final int index, final int flag)
    {
        AVAILABLE_ARRAY.setRelease(availableBuffer, index, flag);
    }

    /**
     * @see Sequencer#isAvailable(long)
     */
    @Override
    public boolean isAvailable(final long sequence)
    {
        // 计算 sequence 对应在 availableBuffer 中的 index 和它应该有的 flag 值（轮数）
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        // 比较应该有的 flag 和实际存储的 flag 值，进而判断它是否可用
        return (int) AVAILABLE_ARRAY.getAcquire(availableBuffer, index) == flag;
    }

    @Override
    public long getHighestPublishedSequence(final long lowerBound, final long availableSequence)
    {
        // 遍历 lowerBound 到 availableSequence 之间的 sequence；如果有不可用的 sequence，就返回它的前一个 sequence
        // 如果都可用，则返回 availableSequence
        for (long sequence = lowerBound; sequence <= availableSequence; sequence++)
        {
            if (!isAvailable(sequence))
            {
                return sequence - 1;
            }
        }

        return availableSequence;
    }

    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift);
    }

    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }

    @Override
    public String toString()
    {
        return "MultiProducerSequencer{" +
                "bufferSize=" + bufferSize +
                ", waitStrategy=" + waitStrategy +
                ", cursor=" + cursor +
                ", gatingSequences=" + Arrays.toString(gatingSequences) +
                '}';
    }
}
