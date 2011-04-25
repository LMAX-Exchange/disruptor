package com.lmax.disruptor.support;


import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.Factory;

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
