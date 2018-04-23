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
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * Set of common functions used by the Disruptor
 */
public final class Util
{
    /**
     * Calculate the next power of 2, greater than or equal to x.<p>
     * From Hacker's Delight, Chapter 3, Harry S. Warren Jr.
     *
     * @param x Value to round up
     * @return The next power of 2 from x inclusive
     */
    public static int ceilingNextPowerOfTwo(final int x)
    {
        return 1 << (32 - Integer.numberOfLeadingZeros(x - 1));
    }

    /**
     * Get the minimum sequence from an array of {@link com.lmax.disruptor.Sequence}s.
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
     * @param sequences to compare.
     * @param minimum   an initial default minimum.  If the array is empty this value will be
     *                  returned.
     * @return the smaller of minimum sequence value found in {@code sequences} and {@code minimum};
     * {@code minimum} if {@code sequences} is empty
     */
    public static long getMinimumSequence(final Sequence[] sequences, long minimum)
    {
        for (int i = 0, n = sequences.length; i < n; i++)
        {
            long value = sequences[i].get();
            minimum = Math.min(minimum, value);
        }

        return minimum;
    }

    /**
     * Get an array of {@link Sequence}s for the passed {@link EventProcessor}s
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

    private static final Unsafe THE_UNSAFE;

    static
    {
        try
        {
            final PrivilegedExceptionAction<Unsafe> action = new PrivilegedExceptionAction<Unsafe>()
            {
                public Unsafe run() throws Exception
                {
                    Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                    theUnsafe.setAccessible(true);
                    return (Unsafe) theUnsafe.get(null);
                }
            };

            THE_UNSAFE = AccessController.doPrivileged(action);
        }
        catch (Exception e)
        {
            throw new RuntimeException("Unable to load unsafe", e);
        }
    }

    /**
     * Get a handle on the Unsafe instance, used for accessing low-level concurrency
     * and memory constructs.
     *
     * @return The Unsafe
     */
    public static Unsafe getUnsafe()
    {
        return THE_UNSAFE;
    }

    /**
     * Calculate the log base 2 of the supplied integer, essentially reports the location
     * of the highest bit.
     *
     * @param i Value to calculate log2 for.
     * @return The log2 value
     */
    public static int log2(int i)
    {
        int r = 0;
        while ((i >>= 1) != 0)
        {
            ++r;
        }
        return r;
    }

    public static long awaitNanos(Object mutex, long timeoutNanos) throws InterruptedException
    {
        long millis = timeoutNanos / 1_000_000;
        long nanos = timeoutNanos % 1_000_000;

        long t0 = System.nanoTime();
        mutex.wait(millis, (int) nanos);
        long t1 = System.nanoTime();

        return timeoutNanos - (t1 - t0);
    }
}
