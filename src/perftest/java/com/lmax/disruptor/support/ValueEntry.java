package com.lmax.disruptor.support;

import com.lmax.disruptor.EntryFactory;
import com.lmax.disruptor.RingBufferEntryBase;

public class ValueEntry extends RingBufferEntryBase
{
    private final static int VALUE_OFFSET = BASE_OFFSET;
    
    public static final EntryFactory<ValueEntry> FACTORY = new EntryFactory<ValueEntry>()
    {
        @Override
        public ValueEntry newInstance()
        {
            return new ValueEntry();
        }
    };
    
    public long getValue()
    {
        return memory.getLong(reference, VALUE_OFFSET);
    }
    
    public void setValue(long value)
    {
        memory.putLong(reference, VALUE_OFFSET, value);        
    }

    @Override
    public int size()
    {
        return 16;
    }
}
