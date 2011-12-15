package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Cache line padded sequence counter.
 *
 * Can be used across threads without worrying about false sharing if a located adjacent to another counter in memory.
 */
public class Sequence
{
    private static final AtomicLongFieldUpdater<Sequence> updater = AtomicLongFieldUpdater.newUpdater(Sequence.class, "value");

    private volatile long p1 = 7L, p2 = 7L, p3 = 7L, p4 = 7L, p5 = 7L, p6 = 7L, p7 = 7L,
                          value = Sequencer.INITIAL_CURSOR_VALUE,
                          q1 = 7L, q2 = 7L, q3 = 7L, q4 = 7L, q5 = 7L, q6 = 7L, q7 = 7L;

    /**
     * Default Constructor that uses an initial value of {@link Sequencer#INITIAL_CURSOR_VALUE}.
     */
    public Sequence()
    {
    }

    /**
     * Construct a sequence counter that can be tracked across threads.
     *
     * @param initialValue for the counter.
     */
    public Sequence(final long initialValue)
    {
        set(initialValue);
    }

    /**
     * Get the current value of the {@link Sequence}
     *
     * @return the current value.
     */
    public long get()
    {
        return value;
    }

    /**
     * Set the {@link Sequence} to a value.
     *
     * @param value to which the {@link Sequence} will be set.
     */
    public void set(final long value)
    {
        updater.lazySet(this, value);
    }

    /**
     * Value of the {@link Sequence} as a String.
     *
     * @return String representation of the sequence.
     */
    public String toString()
    {
        return Long.toString(value);
    }

    /**
     * Atomically compare and set the sequence value.
     *
     * @see java.util.concurrent.atomic.AtomicLong#compareAndSet(long, long)
     * @param expectedSequence to check against
     * @param nextSequence to be set if expectedSequence
     * @return true if the set operation succeeds
     */
    public boolean compareAndSet(long expectedSequence, long nextSequence)
    {
        return updater.compareAndSet(this, expectedSequence, nextSequence);
    }

    /**
     * Here to help make sure false sharing prevention padding is not optimised away.
     *
     * @return sum of padding.
     */
    public long sumPaddingToPreventOptimisation()
    {
        return  p1 + p2 + p3 + p4 + p5 + p6 + p7 + value + q1 + q2 + q3 + q4 + q5 + q6 + q7;
    }

    public void setPaddingValue(final long value)
    {
       p1 = p2 = p3 = p4 = p5 = p6 = p7 = q1 = q2 = q3 = q4 = q5 = q6 = q7 = value;
    }
}
