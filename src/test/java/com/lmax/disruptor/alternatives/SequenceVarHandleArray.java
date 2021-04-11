package com.lmax.disruptor.alternatives;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;


/**
 * Concurrent sequence class used for tracking the progress of
 * the ring buffer and event processors.  Support a number
 * of concurrent operations including CAS and order writes.
 *
 * <p>Also attempts to be more efficient with regards to false
 * sharing by adding padding around the volatile field.
 */
public class SequenceVarHandleArray
{
    private static final int VALUE_INDEX = 8;
    private static final VarHandle VALUE_FIELD = MethodHandles.arrayElementVarHandle(long[].class);

    private final long[] paddedValue = new long[16];

    static final long INITIAL_VALUE = -1L;

    /**
     * Create a sequence initialised to -1.
     */
    public SequenceVarHandleArray()
    {
        this(INITIAL_VALUE);
    }

    /**
     * Create a sequence with a specified initial value.
     *
     * @param initialValue The initial value for this sequence.
     */
    public SequenceVarHandleArray(final long initialValue)
    {
        this.set(initialValue);
    }

    /**
     * Perform a volatile read of this sequence's value.
     *
     * @return The current value of the sequence.
     */
    public long get()
    {
        return (long) (Long) VALUE_FIELD.getAcquire(this.paddedValue, VALUE_INDEX);
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
        VALUE_FIELD.setRelease(this.paddedValue, VALUE_INDEX, value);
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
        VALUE_FIELD.setVolatile(this.paddedValue, VALUE_INDEX, value);
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
        return VALUE_FIELD.compareAndSet(this.paddedValue, VALUE_INDEX, expectedValue, newValue);
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
        return (long) VALUE_FIELD.getAndAdd(this.paddedValue, VALUE_INDEX, increment) + increment;
    }

    @Override
    public String toString()
    {
        return Long.toString(get());
    }
}

