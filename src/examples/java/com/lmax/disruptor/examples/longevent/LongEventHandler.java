package com.lmax.disruptor.examples.longevent;

import com.lmax.disruptor.handler.eventhandler.EventHandler;

// tag::example[]
public class LongEventHandler implements EventHandler<LongEvent>
{
    public void onEvent(LongEvent event, long sequence, boolean endOfBatch)
    {
        System.out.println("Event: " + event);
    }
}
// end::example[]