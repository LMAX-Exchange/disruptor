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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.*;
import com.lmax.disruptor.support.TestEvent;
import com.lmax.disruptor.dsl.stubs.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.yield;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

@SuppressWarnings(value = {"ThrowableResultOfMethodCallIgnored", "unchecked"})
public class DisruptorTest
{
    private static final int TIMEOUT_IN_SECONDS = 2;
    private Disruptor<TestEvent> disruptor;
    private StubExecutor executor;
    private Collection<DelayedEventHandler> delayedEventHandlers = new ArrayList<DelayedEventHandler>();
    private RingBuffer<TestEvent> ringBuffer;
    private TestEvent lastPublishedEvent;

    @Before
    public void setUp() throws Exception
    {
        createDisruptor();
    }

    @After
    public void tearDown() throws Exception
    {
        for (DelayedEventHandler delayedEventHandler : delayedEventHandlers)
        {
            delayedEventHandler.stopWaiting();
        }

        disruptor.halt();
        executor.joinAllThreads();
    }

    @Test
    public void shouldCreateEventProcessorGroupForFirstEventProcessors() throws Exception
    {
        executor.ignoreExecutions();
        final EventHandler<TestEvent> eventHandler1 = new NoOpEventHandler();
        EventHandler<TestEvent> eventHandler2 = new NoOpEventHandler();

        final EventHandlerGroup<TestEvent> eventHandlerGroup = disruptor.handleEventsWith(eventHandler1, eventHandler2);
        disruptor.start();

        assertNotNull(eventHandlerGroup);
        assertThat(Integer.valueOf(executor.getExecutionCount()), equalTo(Integer.valueOf(2)));
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstHandlersImmediately() throws Exception
    {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler = new EventHandlerStub(countDownLatch);

        disruptor.handleEventsWith(createDelayedEventHandler(), eventHandler);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch);
    }

    @Test
    public void shouldWaitUntilAllFirstEventProcessorsProcessEventBeforeMakingItAvailableToDependentEventProcessors() throws Exception
    {
        DelayedEventHandler eventHandler1 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub(countDownLatch);

        disruptor.handleEventsWith(eventHandler1).then(eventHandler2);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, eventHandler1);
    }

    @Test
    public void shouldAllowSpecifyingSpecificEventProcessorsToWaitFor() throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        disruptor.handleEventsWith(handler1, handler2);
        disruptor.after(handler1, handler2).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);
    }

    @Test
    public void shouldWaitOnAllProducersJoinedByAnd() throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        disruptor.handleEventsWith(handler1, handler2);
        disruptor.after(handler1).and(handler2).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerIsNotAlreadyConsuming() throws Exception
    {
        disruptor.after(createDelayedEventHandler()).handleEventsWith(createDelayedEventHandler());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerUsedWithAndIsNotAlreadyConsuming() throws Exception
    {
        final DelayedEventHandler handler1 = createDelayedEventHandler();
        final DelayedEventHandler handler2 = createDelayedEventHandler();
        disruptor.handleEventsWith(handler1);
        disruptor.after(handler1).and(handler2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldTrackEventHandlersByIdentityNotEquality() throws Exception
    {
        EvilEqualsEventHandler handler1 = new EvilEqualsEventHandler();
        EvilEqualsEventHandler handler2 = new EvilEqualsEventHandler();

        disruptor.handleEventsWith(handler1);
        disruptor.after(handler2); // handler2.equals(handler1) but it hasn't yet been registered so should throw exception.
    }

    @Test
    public void shouldSupportSpecifyingADefaultExceptionHandlerForEventProcessors() throws Exception
    {
        AtomicReference<Exception> eventHandled = new AtomicReference<Exception>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.handleExceptionsWith(exceptionHandler);
        disruptor.handleEventsWith(handler);

        publishEvent();

        final Exception actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test
    public void shouldBlockProducerUntilAllEventProcessorsHaveAdvanced() throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);

        final RingBuffer<TestEvent> ringBuffer = disruptor.start();

        final StubPublisher stubPublisher = new StubPublisher(ringBuffer);
        try
        {
            executor.execute(stubPublisher);

            assertProducerReaches(stubPublisher, 4, true);

            delayedEventHandler.processEvent();
            delayedEventHandler.processEvent();
            delayedEventHandler.processEvent();
            delayedEventHandler.processEvent();
            delayedEventHandler.processEvent();

            assertProducerReaches(stubPublisher, 5, false);
        }
        finally
        {
            stubPublisher.halt();
        }
    }

    @Test
    public void shouldBeAbleToOverrideTheExceptionHandlerForAEventProcessor() throws Exception
    {
        final DelayedEventHandler eventHandler = createDelayedEventHandler();

        final RuntimeException testException = new RuntimeException();
        final ExceptionThrowingEventHandler eventHandler2 = new ExceptionThrowingEventHandler(testException);
        disruptor.handleEventsWith(eventHandler).then(eventHandler2);

        publishEvent();

        AtomicReference<Exception> reference = new AtomicReference<Exception>();
        StubExceptionHandler exceptionHandler = new StubExceptionHandler(reference);
        disruptor.handleExceptionsFor(eventHandler2).with(exceptionHandler);
        eventHandler.processEvent();

        waitFor(reference);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenAddingEventProcessorsAfterTheProducerBarrierHasBeenCreated() throws Exception
    {
        executor.ignoreExecutions();
        disruptor.handleEventsWith(new NoOpEventHandler());
        disruptor.start();
        disruptor.handleEventsWith(new NoOpEventHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfStartIsCalledTwice() throws Exception
    {
        executor.ignoreExecutions();
        disruptor.handleEventsWith(new NoOpEventHandler());
        disruptor.start();
        disruptor.start();
    }

    @Test
    public void shouldSupportCustomProcessorsAsDependencies() throws Exception
    {
        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();

        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        final BatchEventProcessor<TestEvent> processor = new BatchEventProcessor<TestEvent>(ringBuffer, ringBuffer.newBarrier(),
                                                                                           delayedEventHandler);
        disruptor.handleEventsWith(processor);
        disruptor.after(processor).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);
    }

    @Test
    public void shouldSupportHandlersAsDependenciesToCustomProcessors() throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);


        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor = new BatchEventProcessor<TestEvent>(ringBuffer, sequenceBarrier, handlerWithBarrier);
        disruptor.handleEventsWith(processor);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);
    }

    @Test
    public void shouldSupportCustomProcessorsAndHandlersAsDependencies() throws Exception
    {
        final DelayedEventHandler delayedEventHandler1 = createDelayedEventHandler();
        final DelayedEventHandler delayedEventHandler2 = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler1);


        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler1).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor = new BatchEventProcessor<TestEvent>(ringBuffer, sequenceBarrier, delayedEventHandler2);

        disruptor.after(delayedEventHandler1).and(processor).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler1, delayedEventHandler2);
    }

    private void ensureTwoEventsProcessedAccordingToDependencies(final CountDownLatch countDownLatch, final DelayedEventHandler... dependencies) throws InterruptedException
    {
        publishEvent();
        publishEvent();

        for (DelayedEventHandler dependency : dependencies)
        {
            assertThatCountDownLatchEquals(countDownLatch, 2L);
            dependency.processEvent();
            dependency.processEvent();
        }

        assertThatCountDownLatchIsZero(countDownLatch);
    }

    private void assertProducerReaches(final StubPublisher stubPublisher, final int expectedPublicationCount, boolean strict)
    {
        long loopStart = System.currentTimeMillis();
        while (stubPublisher.getPublicationCount() < expectedPublicationCount &&
               System.currentTimeMillis() - loopStart < 5000)
        {
            yield();
        }

        if (strict)
        {
            assertThat(Integer.valueOf(stubPublisher.getPublicationCount()), equalTo(Integer.valueOf(expectedPublicationCount)));
        }
        else
        {
            final int actualPublicationCount = stubPublisher.getPublicationCount();
            assertTrue("Producer reached unexpected count. Expected at least " + expectedPublicationCount + " but only reached " + actualPublicationCount,
                      actualPublicationCount >= expectedPublicationCount);
        }
    }

    private void createDisruptor()
    {
        executor = new StubExecutor();
        createDisruptor(executor);
    }

    private void createDisruptor(final Executor executor)
    {
        disruptor = new Disruptor<TestEvent>(TestEvent.EVENT_FACTORY, 4, executor,
                                                         ClaimStrategy.Option.SINGLE_THREADED,
                                                         WaitStrategy.Option.BLOCKING);
    }

    private TestEvent publishEvent()
    {
        if (ringBuffer == null)
        {
            ringBuffer = disruptor.start();
        }

        disruptor.publishEvent(new EventTranslator<TestEvent>()
        {

            @Override
            public TestEvent translateTo(final TestEvent event, final long sequence)
            {
                lastPublishedEvent = event;
                return event;
            }
        });
        return lastPublishedEvent;
    }

    private Exception waitFor(final AtomicReference<Exception> reference)
    {
        while (reference.get() == null)
        {
            yield();
        }
        return reference.get();
    }

    private DelayedEventHandler createDelayedEventHandler()
    {
        final DelayedEventHandler delayedEventHandler = new DelayedEventHandler();
        delayedEventHandlers.add(delayedEventHandler);
        return delayedEventHandler;
    }

    private void assertThatCountDownLatchEquals(final CountDownLatch countDownLatch, final long expectedCountDownValue)
    {
        assertThat(Long.valueOf(countDownLatch.getCount()), equalTo(Long.valueOf(expectedCountDownValue)));
    }

    private void assertThatCountDownLatchIsZero(final CountDownLatch countDownLatch) throws InterruptedException
    {
        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }
}
