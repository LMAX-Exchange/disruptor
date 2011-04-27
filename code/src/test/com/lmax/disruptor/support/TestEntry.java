package com.lmax.disruptor.support;


import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class TestEntry
    extends AbstractEntry
{
    @Override
    public String toString()
    {
        return "Test Entry";
    }

    public final static EntryFactory<TestEntry> ENTRY_FACTORY = new EntryFactory<TestEntry>()
    {
        public TestEntry create()
        {
            return new TestEntry();
        }
    };
}
