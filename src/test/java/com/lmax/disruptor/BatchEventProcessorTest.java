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
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public final class BatchEventProcessorTest
{
    private final Mockery context = new Mockery();

    private final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    @SuppressWarnings("unchecked")
    private final EventHandler<StubEvent> eventHandler = context.mock(EventHandler.class);
    private final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
        ringBuffer, sequenceBarrier, eventHandler);

    {
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        batchEventProcessor.setExceptionHandler(null);
    }

    private static class LifeCycleEventHandler implements LifecycleAware
    {
        private final CountDownLatch startLatch;

        public LifeCycleEventHandler(CountDownLatch startLatch)
        {
            this.startLatch = startLatch;
        }

        @Override
        public void onStart()
        {
            startLatch.countDown();
        }

        @Override
        public void onShutdown()
        {

        }
    }

    private static class LatchEventHandler extends LifeCycleEventHandler implements EventHandler<StubEvent>
    {
        private final CountDownLatch eventLatch;

        public LatchEventHandler(CountDownLatch startLatch, CountDownLatch eventLatch)
        {
            super(startLatch);
            this.eventLatch = eventLatch;
        }

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            eventLatch.countDown();
        }
    }

    private static class ExceptionEventHandler extends LifeCycleEventHandler implements EventHandler<StubEvent>
    {
        private final Exception ex;

        public ExceptionEventHandler(CountDownLatch startLatch, Exception ex)
        {
            super(startLatch);
            this.ex = ex;
        }

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            throw ex;
        }
    }

    private static class LatchExceptionHandler implements ExceptionHandler<StubEvent>
    {
        private final CountDownLatch exceptionLatch;

        public LatchExceptionHandler(CountDownLatch exceptionLatch)
        {
            this.exceptionLatch = exceptionLatch;
        }

        @Override
        public void handleEventException(final Throwable ex, final long sequence, final StubEvent event)
        {
            exceptionLatch.countDown();
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

    @Test
    public void shouldCallMethodsInLifecycleOrder()
        throws Exception
    {
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch eventLatch = new CountDownLatch(1);
        final LatchEventHandler latchProcessor = new LatchEventHandler(startLatch, eventLatch);

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, latchProcessor);

        Thread thread = DaemonThreadFactory.INSTANCE.newThread(batchEventProcessor);
        thread.start();

        assertTrue("Latch never released", startLatch.await(2, TimeUnit.SECONDS));
        assertThat(eventLatch.getCount(), is(1L));

        assertEquals(-1L, batchEventProcessor.getSequence().get());

        ringBuffer.publish(ringBuffer.next());

        assertTrue("Latch never released", eventLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch eventLatch = new CountDownLatch(3);
        final LatchEventHandler latchProcessor = new LatchEventHandler(startLatch, eventLatch);

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, latchProcessor);

        Thread thread = DaemonThreadFactory.INSTANCE.newThread(batchEventProcessor);
        thread.start();

        assertTrue("Latch never released", startLatch.await(2, TimeUnit.SECONDS));
        assertThat(eventLatch.getCount(), is(3L));

        assertEquals(-1L, batchEventProcessor.getSequence().get());

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        assertTrue("Latch never released", eventLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException()
        throws Exception
    {
        final Exception ex = new Exception();
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch exceptionLatch = new CountDownLatch(1);

        ExceptionEventHandler exceptionEventHandler = new ExceptionEventHandler(startLatch, ex);
        LatchExceptionHandler latchExceptionHandler = new LatchExceptionHandler(exceptionLatch);

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, exceptionEventHandler);

        batchEventProcessor.setExceptionHandler(latchExceptionHandler);

        Thread thread = DaemonThreadFactory.INSTANCE.newThread(batchEventProcessor);
        thread.start();

        assertTrue("Latch never released", startLatch.await(2, TimeUnit.SECONDS));

        ringBuffer.publish(ringBuffer.next());

        assertTrue("Latch never released", exceptionLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }
}
