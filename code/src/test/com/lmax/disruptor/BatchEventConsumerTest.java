package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import org.junit.Test;

public final class BatchEventConsumerTest
{
    @Test
    public void shouldDoStuff()
    {
        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.ENTRY_FACTORY, 100);
        ThresholdBarrier<TestEntry> barrier = ringBuffer.createBarrier();
        BatchEventHandler<TestEntry> batchEventHandler = new BatchEventHandler<TestEntry>()
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

        BatchEventConsumer batchEventConsumer = new BatchEventConsumer<TestEntry>(barrier, batchEventHandler);

        batchEventConsumer.halt();
        batchEventConsumer.run();
        batchEventConsumer.getBarrier();
    }
}
