package com.lmax.disruptor.support;

import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class PerfEntry extends AbstractEntry
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

    public final static EntryFactory<PerfEntry> ENTRY_FACTORY = new EntryFactory<PerfEntry>()
    {
        public PerfEntry create()
        {
            return new PerfEntry();
        }
    };
}
