package com.lmax.commons.disruptor.support;


import com.lmax.commons.disruptor.AbstractEntry;
import com.lmax.commons.disruptor.Factory;

public final class TestEntry
    extends AbstractEntry
{
    @Override
    public String toString()
    {
        return "Test Entry";
    }

    public final static Factory<TestEntry> FACTORY = new Factory<TestEntry>()
    {
        public TestEntry create()
        {
            return new TestEntry();
        }
    };
}
