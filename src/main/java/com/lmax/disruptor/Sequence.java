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
    static final long INITIAL_VALUE = -1L;
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
        this(INITIAL_VALUE);
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
     * Perform a volatile read of this sequence's value.
     * 
     * @return The current value of the sequence.
     */
    public long get()
    {
        return unsafe.getLongVolatile(paddedValue, valueOffset);
    }

    /**
     * Perform an ordered write of this sequence.  The intent is
     * a Store/Store barrier between this write and any previous
     * store.
     * 
     * @param value The new value for the sequence.
     */
    public void set(final long value)
    {
        unsafe.putOrderedLong(paddedValue, valueOffset, value);
    }

    /**
     * Performs a volatile write of this sequence.  The intent is
     * a Store/Store barrier between this write and any previous
     * write and a Store/Load barrier between this write and any
     * subsequent volatile read.
     * 
     * @param value The new value for the sequence.
     */
    public void setVolatile(final long value)
    {
        unsafe.putLongVolatile(paddedValue, valueOffset, value);
    }

    /**
     * Perform a compare and set operation on the sequence.
     * 
     * @param expectedValue The expected current value.
     * @param newValue The value to update to.
     * @return true if the operation succeeds, false otherwise.
     */
    public boolean compareAndSet(final long expectedValue, final long newValue)
    {
        return unsafe.compareAndSwapLong(paddedValue, valueOffset, expectedValue, newValue);
    }

    /**
     * Atomically increment the sequence by one.
     * 
     * @return The value after the increment
     */
    public long incrementAndGet()
    {
        return addAndGet(1L);
    }

    /**
     * Atomically add the supplied value.
     * 
     * @param increment The value to add to the sequence.
     * @return The value after the increment.
     */
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

    public String toString()
    {
        return Long.toString(get());
    }
}
