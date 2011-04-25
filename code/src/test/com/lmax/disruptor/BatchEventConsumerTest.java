package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import org.junit.Test;

public final class BatchEventConsumerTest
{
    @Test
    public void shouldDoStuff()
    {
        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.FACTORY, 100);
        ThresholdBarrier<TestEntry> barrier = ringBuffer.createBarrier();
        EventHandler<TestEntry> eventHandler = new EventHandler<TestEntry>()
        {
            @Override
            public void onEvent(final TestEntry entry) throws Exception
            {

            }

            @Override
            public void onEndOfBatch() throws Exception
            {

            }

            @Override
            public void onCompletion()
            {
            }
        };

        BatchEventConsumer batchEventConsumer = new BatchEventConsumer<TestEntry>(barrier, eventHandler);

        batchEventConsumer.halt();
        batchEventConsumer.run();
        batchEventConsumer.getBarrier();
    }
}
