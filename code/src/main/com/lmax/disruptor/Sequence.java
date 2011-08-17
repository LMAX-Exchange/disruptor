package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Cache line padded sequence counter.
 *
 * Can be used across threads without worrying about false sharing if a located adjacent to another counter in memory.
 */
public class Sequence
{
    /**
     * Size of a long array so the object header, value, and padding all fit in a 64 byte cache line.
     */
    public static final int VALUE_PLUS_CACHE_LINE_PADDING = 5;

    private final AtomicLongArray value = new AtomicLongArray(VALUE_PLUS_CACHE_LINE_PADDING);

    /**
     * Construct a sequence counter that can be tracked across threads.
     *
     * @param initialValue for the counter.
     */
    public Sequence(final long initialValue)
    {
        set(initialValue);
    }

    public long get()
    {
        return value.get(0);
    }

    public void set(final long value)
    {
        this.value.lazySet(0, value);
    }
}
