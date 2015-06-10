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

import com.lmax.disruptor.support.StubEvent;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.junit.Assert.assertEquals;

public class SequenceReportingCallbackTest
{
    private final CountDownLatch callbackLatch = new CountDownLatch(1);
    private final CountDownLatch onEndOfBatchLatch = new CountDownLatch(1);

    @Test
    public void shouldReportProgressByUpdatingSequenceViaCallback()
        throws Exception
    {
        final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 16);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
        final SequenceReportingEventHandler<StubEvent> handler = new TestSequenceReportingEventHandler();
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, handler);
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());

        Thread thread = new Thread(batchEventProcessor);
        thread.setDaemon(true);
        thread.start();

        assertEquals(-1L, batchEventProcessor.getSequence().get());
        ringBuffer.publish(ringBuffer.next());

        callbackLatch.await();
        assertEquals(0L, batchEventProcessor.getSequence().get());

        onEndOfBatchLatch.countDown();
        assertEquals(0L, batchEventProcessor.getSequence().get());

        batchEventProcessor.halt();
        thread.join();
    }

    private class TestSequenceReportingEventHandler implements SequenceReportingEventHandler<StubEvent>
    {
        private Sequence sequenceCallback;

        @Override
        public void setSequenceCallback(final Sequence sequenceTrackerCallback)
        {
            this.sequenceCallback = sequenceTrackerCallback;
        }

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            sequenceCallback.set(sequence);
            callbackLatch.countDown();

            if (endOfBatch)
            {
                onEndOfBatchLatch.await();
            }
        }
    }
}
