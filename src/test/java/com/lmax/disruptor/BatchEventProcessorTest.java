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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class BatchEventProcessorTest
{
    private final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, new ExceptionEventHandler());
        batchEventProcessor.setExceptionHandler(null);
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        CountDownLatch eventLatch = new CountDownLatch(3);
        LatchEventHandler eventHandler = new LatchEventHandler(eventLatch);
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, eventHandler);

        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        Thread thread = new Thread(batchEventProcessor);
        thread.start();

        assertTrue(eventLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException()
        throws Exception
    {
        CountDownLatch exceptionLatch = new CountDownLatch(1);
        LatchExceptionHandler latchExceptionHandler = new LatchExceptionHandler(exceptionLatch);
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, new ExceptionEventHandler());
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());

        batchEventProcessor.setExceptionHandler(latchExceptionHandler);

        Thread thread = new Thread(batchEventProcessor);
        thread.start();

        ringBuffer.publish(ringBuffer.next());

        assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }

    private static class LatchEventHandler implements EventHandler<StubEvent>
    {
        private final CountDownLatch latch;

        LatchEventHandler(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onEvent(StubEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            latch.countDown();
        }
    }

    private static class LatchExceptionHandler implements ExceptionHandler<StubEvent>
    {
        private final CountDownLatch latch;

        LatchExceptionHandler(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void handleEventException(Throwable ex, long sequence, StubEvent event)
        {
            latch.countDown();
        }

        @Override
        public void handleOnStartException(Throwable ex)
        {

        }

        @Override
        public void handleOnShutdownException(Throwable ex)
        {

        }
    }

    private static class ExceptionEventHandler implements EventHandler<StubEvent>
    {
        @Override
        public void onEvent(StubEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            throw new NullPointerException(null);
        }
    }

    @Test
    public void reportAccurateBatchSizesAtBatchStartTime()
        throws Exception
    {
        final List<Long> batchSizes = new ArrayList<Long>();
        final CountDownLatch eventLatch = new CountDownLatch(6);

        final class LoopbackEventHandler
            implements EventHandler<StubEvent>, BatchStartAware
        {

            @Override
            public void onBatchStart(long batchSize)
            {
                batchSizes.add(batchSize);
            }

            @Override
            public void onEvent(StubEvent event, long sequence, boolean endOfBatch)
                throws Exception
            {
                if (!endOfBatch)
                {
                    ringBuffer.publish(ringBuffer.next());
                }
                eventLatch.countDown();
            }
        }

        final BatchEventProcessor<StubEvent> batchEventProcessor =
            new BatchEventProcessor<StubEvent>(
                ringBuffer, sequenceBarrier, new LoopbackEventHandler());

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        Thread thread = new Thread(batchEventProcessor);
        thread.start();
        eventLatch.await();

        batchEventProcessor.halt();
        thread.join();

        assertEquals(Arrays.asList(3L, 2L, 1L), batchSizes);
    }
}
