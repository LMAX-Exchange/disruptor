/*
 * Copyright 2012 LMAX Ltd.
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

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import static java.util.Arrays.copyOf;

/**
 * Provides static methods for managing a {@link SequenceGroup} object.
 *
 * <p>提供用于管理{@link SequenceGroup}对象的静态方法。</p>
 */
class SequenceGroups
{
    static <T> void addSequences(
        final T holder, // 代表 sequencer 对象，即 updater 的拥有者
        final AtomicReferenceFieldUpdater<T, Sequence[]> updater, // updater，用于 cas 更新
        final Cursored cursor, // publisher 的游标，用于初始化新添加的 sequences；即新添加的 sequences 的初始值都是 cursor 的值
        final Sequence... sequencesToAdd) // 要添加的 sequences
    {
        // 提前声明变量
        long cursorSequence;
        Sequence[] updatedSequences;
        Sequence[] currentSequences;

        // cas 更新 gatingSequences 字段
        do
        {
            // 获取当前数组
            currentSequences = updater.get(holder);
            // 拷贝得到新的数组，此时新数组中尚未添加新的 sequences；或者可以理解为对应位置的 sequence 元素的 value 是初始值
            updatedSequences = copyOf(currentSequences, currentSequences.length + sequencesToAdd.length);

            // 获取到此时的 publisher 的游标值
            cursorSequence = cursor.getCursor();

            // 将新添加的 sequences 的初始值都设置为 cursor 的值
            int index = currentSequences.length;
            for (Sequence sequence : sequencesToAdd)
            {
                // 更新 sequence 的值
                sequence.set(cursorSequence);
                // 将 sequence 添加到新数组中（元素替换）
                updatedSequences[index++] = sequence;
            }
        }
        while (!updater.compareAndSet(holder, currentSequences, updatedSequences));

        // 再次更新新添加的 sequences 的值
        // 由于存在多线程并发，因此上面的 set 和 cas 之间可能 cursor 的值已经被更新了；所以这里会再 refresh 一次
        cursorSequence = cursor.getCursor();
        for (Sequence sequence : sequencesToAdd)
        {
            sequence.set(cursorSequence);
        }
    }

    static <T> boolean removeSequence(
        final T holder, // 代表 sequencer 对象，即 updater 的拥有者
        final AtomicReferenceFieldUpdater<T, Sequence[]> sequenceUpdater, // updater，用于 cas 更新
        final Sequence sequence) // 要移除的 sequence
    {
        // 提前声明变量
        int numToRemove;
        Sequence[] oldSequences;
        Sequence[] newSequences;

        // cas 更新 gatingSequences 字段
        do
        {
            // 获取当前数组
            oldSequences = sequenceUpdater.get(holder);

            // 统计需要移除的 sequence 的个数
            numToRemove = countMatching(oldSequences, sequence);

            // 如果没有需要移除的 sequence，则直接返回
            if (0 == numToRemove)
            {
                break;
            }

            // 计算新数组的大小
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize - numToRemove];

            // 元素拷贝，跳过需要移除的 sequence
            for (int i = 0, pos = 0; i < oldSize; i++)
            {
                final Sequence testSequence = oldSequences[i];
                if (sequence != testSequence)
                {
                    newSequences[pos++] = testSequence;
                }
            }
        }
        while (!sequenceUpdater.compareAndSet(holder, oldSequences, newSequences));

        return numToRemove != 0;
    }

    private static <T> int countMatching(final T[] values, final T toMatch)
    {
        int numToRemove = 0;
        for (T value : values)
        {
            if (value == toMatch) // Specifically uses identity
            {
                numToRemove++;
            }
        }
        return numToRemove;
    }
}
