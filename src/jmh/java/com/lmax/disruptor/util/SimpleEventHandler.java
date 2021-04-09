package com.lmax.disruptor.util;

import com.lmax.disruptor.EventHandler;
import org.openjdk.jmh.infra.Blackhole;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    private final Blackhole bh;

    public SimpleEventHandler(final Blackhole bh)
    {
        this.bh = bh;
    }

    @Override
    public void onEvent(final SimpleEvent event, final long sequence, final boolean endOfBatch)
    {
        bh.consume(event);
    }
}
