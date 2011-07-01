package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class BatchConsumerSequenceTrackingCallbackTest
{
    private final CountDownLatch callbackLatch = new CountDownLatch(1);
    private final CountDownLatch onEndOfBatchLatch = new CountDownLatch(1);

    @Test
    public void shouldReportProgressByUpdatingSequenceViaCallback()
        throws Exception
    {
        final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 16);
        final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
        final SequenceTrackingHandler<StubEntry> handler = new TestSequenceTrackingHandler();
        final BatchConsumer<StubEntry> batchConsumer = new BatchConsumer<StubEntry>(consumerBarrier, handler);
        final ProducerBarrier<StubEntry> producerBarrier = ringBuffer.createProducerBarrier(batchConsumer);

        Thread thread = new Thread(batchConsumer);
        thread.setDaemon(true);
        thread.start();

        assertEquals(-1L, batchConsumer.getSequence());
        producerBarrier.commit(producerBarrier.nextEntry());

        callbackLatch.await();
        assertEquals(0L, batchConsumer.getSequence());

        onEndOfBatchLatch.countDown();
        assertEquals(0L, batchConsumer.getSequence());

        batchConsumer.halt();
        thread.join();
    }

    private class TestSequenceTrackingHandler implements SequenceTrackingHandler<StubEntry>
    {
        private BatchConsumer.SequenceTrackerCallback sequenceTrackerCallback;

        @Override
        public void setSequenceTrackerCallback(final BatchConsumer.SequenceTrackerCallback sequenceTrackerCallback)
        {
            this.sequenceTrackerCallback = sequenceTrackerCallback;
        }

        @Override
        public void onAvailable(final StubEntry entry) throws Exception
        {
            sequenceTrackerCallback.onCompleted(entry.getSequence());
            callbackLatch.countDown();
        }

        @Override
        public void onEndOfBatch() throws Exception
        {
            onEndOfBatchLatch.await();
        }
    }
}
