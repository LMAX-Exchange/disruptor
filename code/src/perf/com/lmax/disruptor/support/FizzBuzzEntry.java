package com.lmax.disruptor.support;

import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class FizzBuzzEntry extends AbstractEntry
{
    private long value = 0;
    private boolean fizz = false;
    private boolean buzz = false;

    public void reset()
    {
        value = 0L;
        fizz = false;
        buzz = false;
    }

    public long getValue()
    {
        return value;
    }

    public void setValue(final long value)
    {
        this.value = value;
    }

    public boolean isFizz()
    {
        return fizz;
    }

    public void setFizz(final boolean fizz)
    {
        this.fizz = fizz;
    }

    public boolean isBuzz()
    {
        return buzz;
    }

    public void setBuzz(final boolean buzz)
    {
        this.buzz = buzz;
    }


    public final static EntryFactory<FizzBuzzEntry> ENTRY_FACTORY = new EntryFactory<FizzBuzzEntry>()
    {
        public FizzBuzzEntry create()
        {
            return new FizzBuzzEntry();
        }
    };
}
