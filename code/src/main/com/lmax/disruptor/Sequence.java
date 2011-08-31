package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Cache line padded sequence counter.
 *
 * Can be used across threads without worrying about false sharing if a located adjacent to another counter in memory.
 */
public class Sequence
{
    private final PaddedAtomicLong value = new PaddedAtomicLong();

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
        return value.get();
    }

    public void set(final long value)
    {
        this.value.lazySet(value);
    }

    /**
     * Version of AtomicLong with cache line padding to prevent false sharing.
     */
    static class PaddedAtomicLong extends AtomicLong
    {
        public volatile long p1, p2, p3, p4, p5, p6, p7 = 7L;
    }
}
