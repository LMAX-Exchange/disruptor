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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * A {@link Sequence} group that can dynamically have {@link Sequence}s added and removed while being
 * thread safe.
 *
 * <p>一个{@link Sequence}组，可以在线程安全的情况下动态添加和删除{@link Sequence}。</p>
 *
 * <p>The {@link SequenceGroup#get()} and {@link SequenceGroup#set(long)} methods are lock free and can be
 * concurrently be called with the {@link SequenceGroup#add(Sequence)} and {@link SequenceGroup#remove(Sequence)}.
 *
 * <p>{@link SequenceGroup#get()}和{@link SequenceGroup#set(long)}方法是无锁的，
 * 可以与{@link SequenceGroup#add(Sequence)}和{@link SequenceGroup#remove(Sequence)}并发调用。</p>
 */
public final class SequenceGroup extends Sequence
{
    // 针对 sequences 字段的原子更新器
    private static final AtomicReferenceFieldUpdater<SequenceGroup, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(SequenceGroup.class, Sequence[].class, "sequences");
    private volatile Sequence[] sequences = new Sequence[0];

    /**
     * Default Constructor
     */
    public SequenceGroup()
    {
        super(-1);
    }

    /**
     * Get the minimum sequence value for the group.
     *
     * @return the minimum sequence value for the group.
     */
    @Override
    public long get()
    {
        return Util.getMinimumSequence(sequences);
    }

    /**
     * Set all {@link Sequence}s in the group to a given value.
     *
     * <p>将组中的所有{@link Sequence}设置为给定值。</p>
     *
     * @param value to set the group of sequences to.
     */
    @Override
    public void set(final long value)
    {
        final Sequence[] sequences = this.sequences;
        for (Sequence sequence : sequences)
        {
            sequence.set(value);
        }
    }

    /**
     * Add a {@link Sequence} into this aggregate.  This should only be used during
     * initialisation.  Use {@link SequenceGroup#addWhileRunning(Cursored, Sequence)}
     *
     * <p>将{@link Sequence}添加到此聚合中。
     * 这只应在初始化期间使用。
     *
     * @param sequence to be added to the aggregate.
     * @see SequenceGroup#addWhileRunning(Cursored, Sequence)
     */
    public void add(final Sequence sequence)
    {
        Sequence[] oldSequences;
        Sequence[] newSequences;
        do
        {
            oldSequences = sequences;
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize + 1];
            System.arraycopy(oldSequences, 0, newSequences, 0, oldSize);
            newSequences[oldSize] = sequence;
        }
        while (!SEQUENCE_UPDATER.compareAndSet(this, oldSequences, newSequences));
    }

    /**
     * Remove the first occurrence of the {@link Sequence} from this aggregate.
     *
     * <p>从此聚合中删除{@link Sequence}的第一个出现。</p>
     *
     * @param sequence to be removed from this aggregate.
     * @return true if the sequence was removed otherwise false.
     */
    public boolean remove(final Sequence sequence)
    {
        return SequenceGroups.removeSequence(this, SEQUENCE_UPDATER, sequence);
    }

    /**
     * Get the size of the group.
     *
     * <p>获取组的大小。</p>
     *
     * @return the size of the group.
     */
    public int size()
    {
        return sequences.length;
    }

    /**
     * Adds a sequence to the sequence group after threads have started to publish to
     * the Disruptor.  It will set the sequences to cursor value of the ringBuffer
     * just after adding them.  This should prevent any nasty rewind/wrapping effects.
     *
     * <p>在线程开始发布到Disruptor之后，将序列添加到序列组。
     * 它将在添加它们后将序列设置为ringBuffer的游标值。
     * 这应该防止任何不良的倒带/包装效果。</p>
     *
     * @param cursored The data structure that the owner of this sequence group will
     *                 be pulling it's events from.
     * @param sequence The sequence to add.
     */
    public void addWhileRunning(final Cursored cursored, final Sequence sequence)
    {
        SequenceGroups.addSequences(this, SEQUENCE_UPDATER, cursored, sequence);
    }
}
