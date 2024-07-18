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
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * Base class for the various sequencer types (single/multi).  Provides
 * common functionality like the management of gating sequences (add/remove) and
 * ownership of the current cursor.
 *
 * <p>各种顺序器类型（单/多）的基类。
 * 提供了诸如管理门控序列（添加/删除）和拥有当前游标等通用功能。</p>
 */
public abstract class AbstractSequencer implements Sequencer
{
    // 针对 gatingSequences 字段的原子更新器
    // 会在 addGatingSequences() 和 removeGatingSequence() 方法中使用，说明这两个方法可能会被多线程调用
    private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");

    // 对应 RingBuffer 的容量上线
    protected final int bufferSize;
    // 等待策略，注意这个 waitStrategy 是只针对消费者生效的；
    // 也就是说，如果 publisher 在发布消息的时候发现 ringBuffer 满了，也不会使用这个 waitStrategy 来处理
    protected final WaitStrategy waitStrategy;
    // 消息发布指针，代表下一个可用的序号
    protected final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    // 所谓的 gatingSequences，就是一组消费者的序号，用于表示这些消费者已经消费到了哪个位置；
    // 它的增删操作是线程安全的，因为 gatingSequences 字段是用 AtomicReferenceFieldUpdater 来更新的
    protected volatile Sequence[] gatingSequences = new Sequence[0];

    /**
     * Create with the specified buffer size and wait strategy.
     *
     * <p>使用指定的缓冲区大小和等待策略创建。</p>
     *
     * @param bufferSize   The total number of entries, must be a positive power of 2.
     * @param waitStrategy The wait strategy used by this sequencer
     */
    public AbstractSequencer(final int bufferSize, final WaitStrategy waitStrategy)
    {
        if (bufferSize < 1)
        {
            throw new IllegalArgumentException("bufferSize must not be less than 1");
        }
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    /**
     * @see Sequencer#getCursor()
     */
    @Override
    public final long getCursor()
    {
        // 获取当前游标的值，这个游标指消息发布的位置
        return cursor.get();
    }

    /**
     * @see Sequencer#getBufferSize()
     */
    @Override
    public final int getBufferSize()
    {
        // 获取 RingBuffer 的容量上限
        return bufferSize;
    }

    /**
     * @see Sequencer#addGatingSequences(Sequence...)
     */
    @Override
    public final void addGatingSequences(final Sequence... gatingSequences)
    {
        // 增加 gatingSequences，代理给 SequenceGroups 来实现，但实际变更的还是 this.gatingSequences 字段
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, this, gatingSequences);
    }

    /**
     * @see Sequencer#removeGatingSequence(Sequence)
     */
    @Override
    public boolean removeGatingSequence(final Sequence sequence)
    {
        // 移除 gatingSequence，代理给 SequenceGroups 来实现，但实际变更的还是 this.gatingSequences 字段
        return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
    }

    /**
     * @see Sequencer#getMinimumSequence()
     */
    @Override
    public long getMinimumSequence()
    {
        // 获取 gatingSequences 中的最小序列，代表最小的消费者进度
        return Util.getMinimumSequence(gatingSequences, cursor.get());
    }

    /**
     * @see Sequencer#newBarrier(Sequence...)
     */
    @Override
    public SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
    {
        // 构造一个 SequenceBarrier 对象，用于消费者等待
        return new ProcessingSequenceBarrier(this, waitStrategy, cursor, sequencesToTrack);
    }

    /**
     * Creates an event poller for this sequence that will use the supplied data provider and
     * gating sequences.
     *
     * <p>为此序列创建一个 event poller，该轮询器将使用提供的 data provider 和 gating sequences。</p>
     *
     * @param dataProvider    The data source for users of this event poller
     * @param gatingSequences Sequence to be gated on.
     * @return A poller that will gate on this ring buffer and the supplied sequences.
     */
    @Override
    public <T> EventPoller<T> newPoller(final DataProvider<T> dataProvider, final Sequence... gatingSequences)
    {
        return EventPoller.newInstance(dataProvider, this, new Sequence(), cursor, gatingSequences);
    }

    @Override
    public String toString()
    {
        return "AbstractSequencer{" +
            "waitStrategy=" + waitStrategy +
            ", cursor=" + cursor +
            ", gatingSequences=" + Arrays.toString(gatingSequences) +
            '}';
    }
}