package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class BatchEntryConsumerSequenceTrackingCallbackTest
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
        final SequenceTrackingEntryHandler<StubEntry> handler = new TestSequenceTrackingEntryHandler<StubEntry>();
        final BatchEntryConsumer<StubEntry> batchEntryConsumer = new BatchEntryConsumer<StubEntry>(consumerBarrier, handler);

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        assertEquals(-1L, batchEntryConsumer.getSequence());
        producerBarrier.claimNext().commit();
        producerBarrier.claimNext().commit();
        onAvailableLatch.await();
        assertEquals(-1L, batchEntryConsumer.getSequence());

        producerBarrier.claimNext().commit();
        readyToCallbackLatch.await();
        assertEquals(2L, batchEntryConsumer.getSequence());

        batchEntryConsumer.halt();
        thread.join();
    }

    private class TestSequenceTrackingEntryHandler<T> implements SequenceTrackingEntryHandler<StubEntry>
    {
        private BatchEntryConsumer.SequenceTrackerCallback sequenceTrackerCallback;

        @Override
        public void setSequenceTrackerCallback(final BatchEntryConsumer.SequenceTrackerCallback sequenceTrackerCallback)
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
