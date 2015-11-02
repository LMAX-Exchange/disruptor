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

import com.lmax.disruptor.dsl.SequencerFactory;
import com.lmax.disruptor.support.EventHandlerBuilder;
import com.lmax.disruptor.support.SequencerFactories;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public final class BatchEventProcessorTest
{
    private final RingBuffer<StubEvent> ringBuffer;
    private final SequenceBarrier sequenceBarrier;

    public BatchEventProcessorTest(String name, SequencerFactory factory)
    {
        this.ringBuffer = new RingBuffer<>(StubEvent.EVENT_FACTORY, factory.newInstance(16, new BlockingWaitStrategy()));
        this.sequenceBarrier = ringBuffer.newBarrier();
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters()
    {
        return SequencerFactories.asParameters();
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        new BatchEventProcessor<>(ringBuffer, sequenceBarrier, (a, b, c) -> {}).setExceptionHandler(null);
    }

    @Test
    public void shouldCallMethodsInLifecycleOrder()
        throws Exception
    {
        final CountDownLatch startLatch = new CountDownLatch(1);
        final CountDownLatch eventLatch = new CountDownLatch(1);

        final EventHandler<StubEvent> handler =
            EventHandlerBuilder.<StubEvent>aHandler()
                .onEvent((a, b, c) -> eventLatch.countDown())
                .onStart(startLatch::countDown)
                .newInstance();

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, handler);

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

        final EventHandler<StubEvent> handler =
            EventHandlerBuilder.<StubEvent>aHandler()
                .onEvent((a, b, c) -> eventLatch.countDown())
                .onStart(startLatch::countDown)
                .newInstance();

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, handler);

        Thread thread = DaemonThreadFactory.INSTANCE.newThread(batchEventProcessor);
        thread.start();

        assertTrue("Latch never released", startLatch.await(2, TimeUnit.SECONDS));
        assertEquals(-1L, batchEventProcessor.getSequence().get());
        assertThat(eventLatch.getCount(), is(3L));


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

        final EventHandler<StubEvent> handler =
            EventHandlerBuilder.<StubEvent>aHandler()
                .onEvent((a, b, c) -> { throw ex; })
                .onStart(startLatch::countDown)
                .newInstance();

        final BatchEventProcessor<StubEvent> batchEventProcessor = new BatchEventProcessor<StubEvent>(
            ringBuffer, sequenceBarrier, handler);

        batchEventProcessor.setExceptionHandler(((ex1, sequence, event) -> exceptionLatch.countDown()));

        Thread thread = DaemonThreadFactory.INSTANCE.newThread(batchEventProcessor);
        thread.start();

        assertTrue("Latch never released", startLatch.await(2, TimeUnit.SECONDS));

        ringBuffer.publish(ringBuffer.next());

        assertTrue("Latch never released", exceptionLatch.await(2, TimeUnit.SECONDS));

        batchEventProcessor.halt();
        thread.join();
    }
}
