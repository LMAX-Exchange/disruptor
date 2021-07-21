package com.lmax.disruptor.examples.longevent;

import com.lmax.disruptor.EventFactory;

// tag::example[]
public class LongEventFactory implements EventFactory<LongEvent>
{
    public LongEvent newInstance()
    {
        return new LongEvent();
    }
}
// end::example[]