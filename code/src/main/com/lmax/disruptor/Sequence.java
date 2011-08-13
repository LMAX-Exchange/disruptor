package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Volatile sequence counter that is cache line padded.
 */
public class Sequence
{
    private final AtomicLongArray value = new AtomicLongArray(5);

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
