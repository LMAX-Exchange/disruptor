package com.lmax.disruptor.util;

import com.lmax.disruptor.EventHandler;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.atomic.AtomicLong;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    private final Blackhole bh;
    private final AtomicLong eventsHandled;

    public SimpleEventHandler(final Blackhole bh, final AtomicLong eventsHandled)
    {
        this.bh = bh;
        this.eventsHandled = eventsHandled;
    }

    @Override
    public void onEvent(final SimpleEvent event, final long sequence, final boolean endOfBatch)
    {
        eventsHandled.incrementAndGet();
        bh.consume(event);
    }
}
