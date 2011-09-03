package com.lmax.disruptor.support;

import com.lmax.disruptor.EventFactory;

public class LongEvent
{
    private long value;
    
    public void set(long value)
    {
        this.value = value;
    }
    
    public long get()
    {
        return value;
    }
    
    public static final EventFactory<LongEvent> FACTORY = new EventFactory<LongEvent>()
    {
        @Override
        public LongEvent newInstance()
        {
            return new LongEvent();
        }
    };
}
