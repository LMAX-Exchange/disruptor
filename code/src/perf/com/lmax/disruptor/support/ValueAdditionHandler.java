package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;

public final class ValueAdditionHandler implements BatchHandler<ValueEntry>
{
    private long value;

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
        value += entry.getValue();
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }

    @Override
    public void onCompletion()
    {
    }
}
