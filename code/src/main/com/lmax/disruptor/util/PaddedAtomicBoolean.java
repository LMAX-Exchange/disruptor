package com.lmax.disruptor.util;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Version of AtomicBoolean with cache line padding to prevent false sharing.
 */
public class PaddedAtomicBoolean extends AtomicBoolean
{
    public volatile long p1, p2, p3, p4, p5, p6, p7 = 7L;

    /**
     * Default constructor
     */
    public PaddedAtomicBoolean()
    {
    }

    /**
     * Construct with an initial value.
     *
     * @param initialValue for initialisation
     */
    public PaddedAtomicBoolean(final boolean initialValue)
    {
        super(initialValue);
    }
}
