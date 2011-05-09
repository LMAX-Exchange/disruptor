package com.lmax.disruptor.support;

import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class ValueEntry extends AbstractEntry
{
    private long value;

    public long getValue()
    {
        return value;
    }

    public void setValue(final long value)
    {
        this.value = value;
    }

    public final static EntryFactory<ValueEntry> ENTRY_FACTORY = new EntryFactory<ValueEntry>()
    {
        public ValueEntry create()
        {
            return new ValueEntry();
        }
    };
}
