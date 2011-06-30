package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;

public final class ValueMutationHandler implements BatchHandler<ValueEntry>
{
    private final Operation operation;
    private long value;

    public ValueMutationHandler(final Operation operation)
    {
        this.operation = operation;
    }

    public long getValue()
    {
        return value;
    }

    public void reset()
    {
        value = 0L;
    }

    @Override
    public void onAvailable(final ValueEntry entry) throws Exception
    {
        value = operation.op(value, entry.getValue());
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }
}
