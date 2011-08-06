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

import static org.junit.Assert.assertEquals;

public class BatchEventProcessorSequenceNotifyingCallbackTest
{
    private final CountDownLatch callbackLatch = new CountDownLatch(1);
    private final CountDownLatch onEndOfBatchLatch = new CountDownLatch(1);

    @Test
    public void shouldReportProgressByUpdatingSequenceViaCallback()
        throws Exception
    {
        final RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 16);
        final DependencyBarrier<StubEvent> dependencyBarrier = ringBuffer.createDependencyBarrier();
        final SequenceNotifyingEventHandler<StubEvent> handler = new TestSequenceNotifyingEventHandler();
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(dependencyBarrier, handler);
        ringBuffer.setTrackedProcessors(batchEventProcessor);

        Thread thread = new Thread(batchEventProcessor);
        thread.setDaemon(true);
        thread.start();

        assertEquals(-1L, batchEventProcessor.getSequence());
        ringBuffer.publish(ringBuffer.nextEvent());

        callbackLatch.await();
        assertEquals(0L, batchEventProcessor.getSequence());

        onEndOfBatchLatch.countDown();
        assertEquals(0L, batchEventProcessor.getSequence());

        batchEventProcessor.halt();
        thread.join();
    }

    private class TestSequenceNotifyingEventHandler implements SequenceNotifyingEventHandler<StubEvent>
    {
        private Sequence sequenceCallback;

        @Override
        public void setSequenceCallback(final Sequence sequenceTrackerCallback)
        {
            this.sequenceCallback = sequenceTrackerCallback;
        }

        @Override
        public void onAvailable(final StubEvent event) throws Exception
        {
            sequenceCallback.set(event.getSequence());
            callbackLatch.countDown();
        }

        @Override
        public void onEndOfBatch() throws Exception
        {
            onEndOfBatchLatch.await();
        }
    }
}
