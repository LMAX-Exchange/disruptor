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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class BatchEventProcessorTest
{
    private final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

    @Test
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        assertThrows(NullPointerException.class, () ->
        {
            final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<>(
                    ringBuffer, sequenceBarrier, new ExceptionEventHandler());
            batchEventProcessor.setExceptionHandler(null);
        });
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        CountDownLatch eventLatch = new CountDownLatch(3);
        LatchEventHandler eventHandler = new LatchEventHandler(eventLatch);
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<>(
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
        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<>(
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

        LatchEventHandler(final CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            latch.countDown();
        }
    }

    private static class LatchExceptionHandler implements ExceptionHandler<StubEvent>
    {
        private final CountDownLatch latch;

        LatchExceptionHandler(final CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void handleEventException(final Throwable ex, final long sequence, final StubEvent event)
        {
            latch.countDown();
        }

        @Override
        public void handleOnStartException(final Throwable ex)
        {

        }

        @Override
        public void handleOnShutdownException(final Throwable ex)
        {

        }
    }

    private static class ExceptionEventHandler implements EventHandler<StubEvent>
    {
        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            throw new NullPointerException(null);
        }
    }

    @Test
    public void reportAccurateBatchSizesAtBatchStartTime()
        throws Exception
    {
        final List<Long> batchSizes = new ArrayList<>();
        final CountDownLatch eventLatch = new CountDownLatch(6);

        final class LoopbackEventHandler
            implements EventHandler<StubEvent>, BatchStartAware
        {

            @Override
            public void onBatchStart(final long batchSize)
            {
                batchSizes.add(batchSize);
            }

            @Override
            public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch)
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
                new BatchEventProcessor<>(
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

    @Test
    public void shouldAlwaysHalt() throws InterruptedException
    {
        WaitStrategy waitStrategy = new BusySpinWaitStrategy();
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(8, waitStrategy);
        final ProcessingSequenceBarrier barrier = new ProcessingSequenceBarrier(
            sequencer, waitStrategy, new Sequence(-1), new Sequence[0]);
        DataProvider<Object> dp = sequence -> null;

        final LatchLifeCycleHandler h1 = new LatchLifeCycleHandler();
        final BatchEventProcessor p1 = new BatchEventProcessor<>(dp, barrier, h1);

        Thread t1 = new Thread(p1);
        p1.halt();
        t1.start();

        assertTrue(h1.awaitStart(2, TimeUnit.SECONDS));
        assertTrue(h1.awaitStop(2, TimeUnit.SECONDS));

        for (int i = 0; i < 1000; i++)
        {
            final LatchLifeCycleHandler h2 = new LatchLifeCycleHandler();
            final BatchEventProcessor p2 = new BatchEventProcessor<>(dp, barrier, h2);
            Thread t2 = new Thread(p2);
            t2.start();
            p2.halt();

            assertTrue(h2.awaitStart(2, TimeUnit.SECONDS));
            assertTrue(h2.awaitStop(2, TimeUnit.SECONDS));
        }

        for (int i = 0; i < 1000; i++)
        {
            final LatchLifeCycleHandler h2 = new LatchLifeCycleHandler();
            final BatchEventProcessor p2 = new BatchEventProcessor<>(dp, barrier, h2);
            Thread t2 = new Thread(p2);
            t2.start();
            Thread.yield();
            p2.halt();

            assertTrue(h2.awaitStart(2, TimeUnit.SECONDS));
            assertTrue(h2.awaitStop(2, TimeUnit.SECONDS));
        }
    }

    private static class LatchLifeCycleHandler implements EventHandler<Object>, LifecycleAware
    {
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final CountDownLatch stopLatch = new CountDownLatch(1);

        @Override
        public void onEvent(final Object event, final long sequence, final boolean endOfBatch) throws Exception
        {

        }

        @Override
        public void onStart()
        {
            startLatch.countDown();
        }

        @Override
        public void onShutdown()
        {
            stopLatch.countDown();
        }

        public boolean awaitStart(final long time, final TimeUnit unit) throws InterruptedException
        {
            return startLatch.await(time, unit);
        }


        public boolean awaitStop(final long time, final TimeUnit unit) throws InterruptedException
        {
            return stopLatch.await(time, unit);
        }
    }

    @Test
    public void shouldNotPassZeroSizeToBatchStartAware() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(3);

        BatchAwareEventHandler eventHandler = new BatchAwareEventHandler(latch);

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<>(
                ringBuffer, new DelegatingSequenceBarrier(this.sequenceBarrier), eventHandler);

        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());

        Thread thread = new Thread(batchEventProcessor);
        thread.start();
        latch.await(2, TimeUnit.SECONDS);

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        batchEventProcessor.halt();
        thread.join();

        assertThat(eventHandler.batchSizeToCountMap.size(), not(0));
        assertThat(eventHandler.batchSizeToCountMap.get(0L), nullValue());
    }

    private static class DelegatingSequenceBarrier implements SequenceBarrier
    {
        private SequenceBarrier delegate;
        private boolean suppress = true;

        DelegatingSequenceBarrier(final SequenceBarrier delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public long waitFor(final long sequence) throws AlertException, InterruptedException, TimeoutException
        {
            long result = suppress ? sequence - 1 : delegate.waitFor(sequence);
            suppress = !suppress;
            return result;
        }

        @Override
        public long getCursor()
        {
            return delegate.getCursor();
        }

        @Override
        public boolean isAlerted()
        {
            return delegate.isAlerted();
        }

        @Override
        public void alert()
        {
            delegate.alert();
        }

        @Override
        public void clearAlert()
        {
            delegate.clearAlert();
        }

        @Override
        public void checkAlert() throws AlertException
        {
            delegate.checkAlert();
        }
    }

    private static class BatchAwareEventHandler extends LatchEventHandler implements BatchStartAware
    {
        final Map<Long, Integer> batchSizeToCountMap = new HashMap<>();

        BatchAwareEventHandler(final CountDownLatch latch)
        {
            super(latch);
        }

        @Override
        public void onBatchStart(final long batchSize)
        {
            final Integer currentCount = batchSizeToCountMap.get(batchSize);
            final int nextCount = null == currentCount ? 1 : currentCount + 1;
            batchSizeToCountMap.put(batchSize, nextCount);
        }
    }

}
