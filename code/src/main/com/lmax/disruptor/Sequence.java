package com.lmax.disruptor;

import com.lmax.disruptor.util.PaddedAtomicLong;

/**
 * Cache line padded sequence counter.
 *
 * Can be used across threads without worrying about false sharing if a located adjacent to another counter in memory.
 */
public class Sequence
{
    private final PaddedAtomicLong value = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);

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
        return value.get();
    }

    /**
     * Set the {@link Sequence} to a value.
     *
     * @param value to which the {@link Sequence} will be set.
     */
    public void set(final long value)
    {
        this.value.lazySet(value);
    }

    /**
     * Value of the {@link Sequence} as a String.
     *
     * @return String representation of the sequence.
     */
    public String toString()
    {
        return Long.toString(value.get());
    }
}
