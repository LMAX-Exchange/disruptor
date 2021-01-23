package com.lmax.disruptor.alternatives;


import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

class LhsPaddingVarHandleBarrier
{
    protected long p1, p2, p3, p4, p5, p6, p7;
}

class ValueVarHandleBarrier extends LhsPaddingVarHandleBarrier
{
    protected long value;
}

class RhsPaddingVarHandleBarrier extends ValueVarHandleBarrier
{
    protected long p9, p10, p11, p12, p13, p14, p15;
}

/**
 * <p>Concurrent sequence class used for tracking the progress of
 * the ring buffer and event processors.  Support a number
 * of concurrent operations including CAS and order writes.
 *
 * <p>Also attempts to be more efficient with regards to false
 * sharing by adding padding around the volatile field.
 */
public class SequenceVarHandleBarrier extends RhsPaddingVarHandleBarrier
{
    static final long INITIAL_VALUE = -1L;
    private static final VarHandle VALUE_FIELD;

    static
    {
        try
        {
            VALUE_FIELD = MethodHandles.lookup().in(SequenceVarHandleBarrier.class)
                    .findVarHandle(SequenceVarHandleBarrier.class, "value", long.class);
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Create a sequence initialised to -1.
     */
    public SequenceVarHandleBarrier()
    {
        this(INITIAL_VALUE);
    }

    /**
     * Create a sequence with a specified initial value.
     *
     * @param initialValue The initial value for this sequence.
     */
    public SequenceVarHandleBarrier(final long initialValue)
    {
        VarHandle.releaseFence();
        this.value = initialValue;
    }

    /**
     * Perform a volatile read of this sequence's value.
     *
     * @return The current value of the sequence.
     */
    public long get()
    {
        long value = this.value;
        VarHandle.acquireFence();
        return value;
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
        VarHandle.releaseFence();
        this.value = value;
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
        VarHandle.releaseFence();
        this.value = value;
        VarHandle.fullFence();
    }

    /**
     * Perform a compare and set operation on the sequence.
     *
     * @param expectedValue The expected current value.
     * @param newValue      The value to update to.
     * @return true if the operation succeeds, false otherwise.
     */
    public boolean compareAndSet(final long expectedValue, final long newValue)
    {
        return (boolean) VALUE_FIELD.compareAndSet(this, expectedValue, newValue);
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
        long v;
        do
        {
            v = value;
            VarHandle.fullFence();
        }
        while (!compareAndSet(v, v + increment));

        return v;
    }

    @Override
    public String toString()
    {
        return Long.toString(get());
    }
}