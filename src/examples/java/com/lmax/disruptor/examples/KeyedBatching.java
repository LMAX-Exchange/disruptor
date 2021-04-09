package com.lmax.disruptor.examples;

import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class KeyedBatching implements EventHandler<KeyedBatching.KeyedEvent>
{
    private static final int MAX_BATCH_SIZE = 100;
    private final List<Object> batch = new ArrayList<>();
    private long key = 0;

    @Override
    public void onEvent(final KeyedEvent event, final long sequence, final boolean endOfBatch)
    {
        if (!batch.isEmpty() && event.key != key)
        {
            processBatch(batch);
        }

        batch.add(event.data);
        key = event.key;

        if (endOfBatch || batch.size() >= MAX_BATCH_SIZE)
        {
            processBatch(batch);
        }
    }

    private void processBatch(final List<Object> batch)
    {
        // do work.
        batch.clear();
    }

    public static class KeyedEvent
    {
        long key;
        Object data;
    }
}
