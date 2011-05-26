package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class BatchConsumerSequenceTrackingCallbackTest
{
    private final CountDownLatch onAvailableLatch = new CountDownLatch(2);
    private final CountDownLatch readyToCallbackLatch = new CountDownLatch(1);

    @Test
    public void shouldReportProgressByUpdatingSequenceViaCallback()
        throws Exception
    {
        final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 16);
        final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
        final ProducerBarrier<StubEntry> producerBarrier = ringBuffer.createProducerBarrier(0);
        final SequenceTrackingHandler<StubEntry> handler = new TestSequenceTrackingHandler();
        final BatchConsumer<StubEntry> batchConsumer = new BatchConsumer<StubEntry>(consumerBarrier, handler);

        Thread thread = new Thread(batchConsumer);
        thread.start();

        assertEquals(-1L, batchConsumer.getSequence());
        producerBarrier.commit(producerBarrier.claim());
        producerBarrier.commit(producerBarrier.claim());
        onAvailableLatch.await();
        assertEquals(-1L, batchConsumer.getSequence());

        producerBarrier.commit(producerBarrier.claim());
        readyToCallbackLatch.await();
        assertEquals(2L, batchConsumer.getSequence());

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
            if (entry.getSequence() == 2L)
            {
                sequenceTrackerCallback.onCompleted(entry.getSequence());
                readyToCallbackLatch.countDown();
            }
            else
            {
                onAvailableLatch.countDown();
            }
        }

        @Override
        public void onEndOfBatch() throws Exception
        {
        }

        @Override
        public void onCompletion()
        {
        }
    }
}
