/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
        ringBuffer.setTrackedConsumers(batchConsumer);

        Thread thread = new Thread(batchConsumer);
        thread.setDaemon(true);
        thread.start();

        assertEquals(-1L, batchConsumer.getSequence());
        ringBuffer.commit(ringBuffer.nextEntry());

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
