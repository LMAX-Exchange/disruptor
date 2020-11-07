package com.lmax.disruptor.util;

import com.lmax.disruptor.EventHandler;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    private final AtomicLong eventsHandled;

    public SimpleEventHandler(final AtomicLong eventsHandled)
    {
        this.eventsHandled = eventsHandled;
    }

    @Override
    public void onEvent(SimpleEvent event, long sequence, boolean endOfBatch)
    {
        eventsHandled.incrementAndGet();
    }
}
