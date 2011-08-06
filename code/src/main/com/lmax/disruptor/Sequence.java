package com.lmax.disruptor;

/**
 * Volatile sequence counter that is cache line padded.
 */
public final class Sequence
{
    private volatile long value;
    public long p1, p2, p3, p4, p5, p6, p7; // cache line padding

    public Sequence(final long initialValue)
    {
        this.value = initialValue;
    }

    public long get()
    {
        return value;
    }

    public void set(final long value)
    {
        this.value = value;
    }
}
