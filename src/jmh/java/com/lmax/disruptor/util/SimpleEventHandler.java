package com.lmax.disruptor.util;

import com.lmax.disruptor.EventHandler;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    public long lastSeenSequence = Long.MIN_VALUE;

    @Override
    public void onEvent(SimpleEvent event, long sequence, boolean endOfBatch)
    {
        lastSeenSequence = sequence;
    }
}
