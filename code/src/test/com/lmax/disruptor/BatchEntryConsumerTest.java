package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import org.junit.Test;

public final class BatchEntryConsumerTest
{
    @Test
    public void shouldDoStuff()
    {
        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.ENTRY_FACTORY, 100);
        ThresholdBarrier<TestEntry> barrier = ringBuffer.createBarrier();
        BatchEntryHandler<TestEntry> batchEntryHandler = new BatchEntryHandler<TestEntry>()
        {
            @Override
            public void onAvailable(final TestEntry entry) throws Exception
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

        BatchEntryConsumer batchEntryConsumer = new BatchEntryConsumer<TestEntry>(barrier, batchEntryHandler);

        batchEntryConsumer.halt();
        batchEntryConsumer.run();
        batchEntryConsumer.getBarrier();
    }
}
