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

import com.lmax.disruptor.util.Util;

import sun.misc.Unsafe;

/**
 * <p>Concurrent sequence class used for tracking the progress of
 * the ring buffer and event processors.  Support a number
 * of concurrent operations including CAS and order writes.
 * 
 * <p>Also attempts to be more efficient with regards to false
 * sharing by adding padding around the volatile field.
 */
public class Sequence
{
    private static final Unsafe unsafe;
    private static final long valueOffset;

    static
    {
        unsafe = Util.getUnsafe();
        final int base = unsafe.arrayBaseOffset(long[].class);
        final int scale = unsafe.arrayIndexScale(long[].class);
        valueOffset = base + (scale * 7);
    }

    private final long[] paddedValue = new long[15];

    /**
     * Create a sequence initialised to -1.
     */
    public Sequence()
    {
        this(-1L);
    }

    /**
     * Create a sequence with a specified initial value.
     * 
     * @param initialValue The initial value for this sequence.
     */
    public Sequence(final long initialValue)
    {
        unsafe.putOrderedLong(paddedValue, valueOffset, initialValue);
    }

    /**
     * Performance a volatile read of this sequence's value.
     * 
     * @return The current value of the sequence.
     */
    public long get()
    {
        return unsafe.getLongVolatile(paddedValue, valueOffset);
    }

    public void setOrdered(final long value)
    {
        unsafe.putOrderedLong(paddedValue, valueOffset, value);
    }

    public void setVolatile(final long value)
    {
        unsafe.putLongVolatile(paddedValue, valueOffset, value);
    }

    public boolean compareAndSet(final long expectedValue, final long newValue)
    {
        return unsafe.compareAndSwapLong(paddedValue, valueOffset, expectedValue, newValue);
    }

    public String toString()
    {
        return Long.toString(get());
    }

    public long incrementAndGet()
    {
        return addAndGet(1L);
    }

    public long addAndGet(final long increment)
    {
        long currentValue;
        long newValue;

        do
        {
            currentValue = get();
            newValue = currentValue + increment;
        }
        while (!compareAndSet(currentValue, newValue));

        return newValue;
    }
}
