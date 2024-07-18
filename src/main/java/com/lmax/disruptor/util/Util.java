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
package com.lmax.disruptor.util;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;

/**
 * Set of common functions used by the Disruptor.
 *
 * <p>Disruptor使用的一组常用函数。</p>
 */
public final class Util
{
    private static final int ONE_MILLISECOND_IN_NANOSECONDS = 1_000_000;

    /**
     * Calculate the next power of 2, greater than or equal to x.
     *
     * <p>计算大于或等于x的下一个2的幂。</p>
     *
     * <p>From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
     *
     * @param x Value to round up
     * @return The next power of 2 from x inclusive
     */
    public static int ceilingNextPowerOfTwo(final int x)
    {
        return 1 << (Integer.SIZE - Integer.numberOfLeadingZeros(x - 1));
    }

    /**
     * Get the minimum sequence from an array of {@link com.lmax.disruptor.Sequence}s.
     *
     * <p>从{@link com.lmax.disruptor.Sequence}数组中获取最小序列。</p>
     *
     * @param sequences to compare.
     * @return the minimum sequence found or Long.MAX_VALUE if the array is empty.
     */
    public static long getMinimumSequence(final Sequence[] sequences)
    {
        return getMinimumSequence(sequences, Long.MAX_VALUE);
    }

    /**
     * Get the minimum sequence from an array of {@link com.lmax.disruptor.Sequence}s.
     *
     * <p>从{@link com.lmax.disruptor.Sequence}数组中获取最小序列。</p>
     *
     * @param sequences to compare.
     * @param minimum   an initial default minimum.  If the array is empty this value will be
     *                  returned.
     * @return the smaller of minimum sequence value found in {@code sequences} and {@code minimum};
     * {@code minimum} if {@code sequences} is empty
     */
    public static long getMinimumSequence(final Sequence[] sequences, final long minimum)
    {
        // 声明内部变量，记录最小序列
        long minimumSequence = minimum;
        // 遍历所有序列，找到最小的序列
        for (int i = 0, n = sequences.length; i < n; i++)
        {
            long value = sequences[i].get();
            minimumSequence = Math.min(minimumSequence, value);
        }

        return minimumSequence;
    }

    /**
     * Get an array of {@link Sequence}s for the passed {@link EventProcessor}s.
     *
     * <p>获取传递的{@link EventProcessor}的{@link Sequence}数组。</p>
     *
     * @param processors for which to get the sequences
     * @return the array of {@link Sequence}s
     */
    public static Sequence[] getSequencesFor(final EventProcessor... processors)
    {
        Sequence[] sequences = new Sequence[processors.length];
        for (int i = 0; i < sequences.length; i++)
        {
            sequences[i] = processors[i].getSequence();
        }

        return sequences;
    }

    /**
     * Calculate the log base 2 of the supplied integer, essentially reports the location
     * of the highest bit.
     *
     * <p>计算所提供整数的以2为底的对数，基本上报告最高位的位置。</p>
     *
     * @param value Positive value to calculate log2 for.
     * @return The log2 value
     */
    public static int log2(final int value)
    {
        if (value < 1)
        {
            throw new IllegalArgumentException("value must be a positive number");
        }
        return Integer.SIZE - Integer.numberOfLeadingZeros(value) - 1;
    }

    /**
     * @param mutex The object to wait on
     * @param timeoutNanos The number of nanoseconds to wait for
     * @return the number of nanoseconds waited (approximately)
     * @throws InterruptedException if the underlying call to wait is interrupted
     */
    public static long awaitNanos(final Object mutex, final long timeoutNanos) throws InterruptedException
    {
        // 计算等待时间
        long millis = timeoutNanos / ONE_MILLISECOND_IN_NANOSECONDS;
        long nanos = timeoutNanos % ONE_MILLISECOND_IN_NANOSECONDS;

        long t0 = System.nanoTime();
        // 触发等待
        mutex.wait(millis, (int) nanos);
        long t1 = System.nanoTime();

        // 返回实际等待时间
        return timeoutNanos - (t1 - t0);
    }
}
