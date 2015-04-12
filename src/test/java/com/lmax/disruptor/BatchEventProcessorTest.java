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
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static com.lmax.disruptor.support.Actions.countDown;
import static org.junit.Assert.assertEquals;

@RunWith(JMock.class)
public final class BatchEventProcessorTest
{
    private final Mockery context = new Mockery();
    private final Sequence lifecycleSequence = context.sequence("lifecycleSequence");
    private final CountDownLatch latch = new CountDownLatch(1);

    private final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    @SuppressWarnings("unchecked") private final EventHandler<StubEvent> eventHandler = context.mock(EventHandler.class);
    private final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(ringBuffer, sequenceBarrier, eventHandler);
    {
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());
    }

    @SuppressWarnings("unchecked")
    private final ExceptionHandler<StubEvent> exceptionHandler = context.mock(ExceptionHandler.class);

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        batchEventProcessor.setExceptionHandler(null);
    }

    @Test
    public void shouldCallMethodsInLifecycleOrder()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(eventHandler).onEvent(ringBuffer.get(0L), 0L, true);
                inSequence(lifecycleSequence);

                will(countDown(latch));
            }
        });

        Thread thread = new Thread(batchEventProcessor);
        thread.start();

        assertEquals(-1L, batchEventProcessor.getSequence().get());

        ringBuffer.publish(ringBuffer.next());

        latch.await();

        batchEventProcessor.halt();
        thread.join();
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(eventHandler).onEvent(ringBuffer.get(0L), 0L, false);
                inSequence(lifecycleSequence);
                oneOf(eventHandler).onEvent(ringBuffer.get(1L), 1L, false);
                inSequence(lifecycleSequence);
                oneOf(eventHandler).onEvent(ringBuffer.get(2L), 2L, true);
                inSequence(lifecycleSequence);

                will(countDown(latch));
            }
        });

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        Thread thread = new Thread(batchEventProcessor);
        thread.start();

        latch.await();

        batchEventProcessor.halt();
        thread.join();
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException()
        throws Exception
    {
        final Exception ex = new Exception();
        batchEventProcessor.setExceptionHandler(exceptionHandler);

        context.checking(new Expectations()
        {
            {
                oneOf(eventHandler).onEvent(ringBuffer.get(0), 0L, true);
                inSequence(lifecycleSequence);
                will(new Action()
                {
                    @Override
                    public Object invoke(final Invocation invocation) throws Throwable
                    {
                        throw ex;
                    }

                    @Override
                    public void describeTo(final Description description)
                    {
                        description.appendText("Throws exception");
                    }
                });

                oneOf(exceptionHandler).handleEventException(ex, 0L, ringBuffer.get(0));
                inSequence(lifecycleSequence);
                will(countDown(latch));
            }
        });

        Thread thread = new Thread(batchEventProcessor);
        thread.start();

        ringBuffer.publish(ringBuffer.next());

        latch.await();

        batchEventProcessor.halt();
        thread.join();
    }
}
