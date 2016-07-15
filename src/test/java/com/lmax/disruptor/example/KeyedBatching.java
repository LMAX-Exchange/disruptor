package com.lmax.disruptor.example;

import com.lmax.disruptor.EventHandler;

import java.util.ArrayList;
import java.util.List;

public class KeyedBatching implements EventHandler<KeyedBatching.KeyedEvent>
{
    private static final int MAX_BATCH_SIZE = 100;
    private long key = 0;
    private List<Object> batch = new ArrayList<Object>();

    @Override
    public void onEvent(KeyedEvent event, long sequence, boolean endOfBatch) throws Exception
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

    private void processBatch(List<Object> batch)
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
