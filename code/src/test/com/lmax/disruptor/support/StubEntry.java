package com.lmax.disruptor.support;

import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class StubEntry extends AbstractEntry
{
    private int value;
    private String testString;

    public StubEntry(int i)
    {
        this.value = i;
    }

    public void copy(StubEntry entry)
    {
        value = entry.value;
    }

    public int getValue()
    {
        return value;
    }

    public void setValue(int value)
    {
        this.value = value;
    }

    public String getTestString()
    {
        return testString;
    }

    public void setTestString(final String testString)
    {
        this.testString = testString;
    }

    public final static EntryFactory<StubEntry> ENTRY_FACTORY = new EntryFactory<StubEntry>()
    {
        public StubEntry create()
        {
            return new StubEntry(-1);
        }
    };

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + value;
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        StubEntry other = (StubEntry)obj;

        return value == other.value;
    }
}
