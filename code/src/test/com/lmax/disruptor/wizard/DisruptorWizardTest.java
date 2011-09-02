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
package com.lmax.disruptor.wizard;

import com.lmax.disruptor.*;
import com.lmax.disruptor.support.TestEvent;
import com.lmax.disruptor.wizard.stubs.*;
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
public class DisruptorWizardTest
{
    private static final int TIMEOUT_IN_SECONDS = 2;
    private DisruptorWizard<TestEvent> disruptorWizard;
    private StubExecutor executor;
    private Collection<DelayedEventHandler> delayedEventHandlers = new ArrayList<DelayedEventHandler>();
    private RingBuffer<TestEvent> ringBuffer;

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

        disruptorWizard.halt();
        executor.joinAllThreads();
    }

    @Test
    public void shouldCreateEventProcessorGroupForFirstEventProcessors() throws Exception
    {
        executor.ignoreExecutions();
        final EventHandler<TestEvent> eventHandler1 = new NoOpEventHandler();
        EventHandler<TestEvent> eventHandler2 = new NoOpEventHandler();

        final EventHandlerGroup<TestEvent> eventHandlerGroup = disruptorWizard.handleEventsWith(eventHandler1, eventHandler2);
        disruptorWizard.start();

        assertNotNull(eventHandlerGroup);
        assertThat(Integer.valueOf(executor.getExecutionCount()), equalTo(Integer.valueOf(2)));
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstHandlersImmediately() throws Exception
    {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler = new EventHandlerStub(countDownLatch);

        disruptorWizard.handleEventsWith(createDelayedEventHandler(), eventHandler);

        publishEvent();
        publishEvent();

        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldWaitUntilAllFirstEventProcessorsProcessEventBeforeMakingItAvailableToDependentEventProcessors() throws Exception
    {
        DelayedEventHandler eventHandler1 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub(countDownLatch);

        disruptorWizard.handleEventsWith(eventHandler1).then(eventHandler2);

        publishEvent();
        publishEvent();

        assertThatCountDownLatchEquals(countDownLatch, 2L);

        eventHandler1.processEvent();
        eventHandler1.processEvent();
        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldAllowSpecifyingSpecificEventProcessorsToWaitFor() throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        disruptorWizard.handleEventsWith(handler1, handler2);
        disruptorWizard.after(handler1, handler2).handleEventsWith(handlerWithBarrier);

        publishEvent();
        publishEvent();

        assertThatCountDownLatchEquals(countDownLatch, 2L);

        handler1.processEvent();
        handler2.processEvent();

        assertThatCountDownLatchEquals(countDownLatch, 2L);

        handler2.processEvent();
        handler1.processEvent();
        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldWaitOnAllProducersJoinedByAnd() throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub(countDownLatch);

        disruptorWizard.handleEventsWith(handler1, handler2);
        disruptorWizard.after(handler1).and(handler2).handleEventsWith(handlerWithBarrier);

        publishEvent();
        publishEvent();

        assertThatCountDownLatchEquals(countDownLatch, 2L);

        handler1.processEvent();
        handler1.processEvent();
        assertThatCountDownLatchEquals(countDownLatch, 2L);

        handler2.processEvent();
        handler2.processEvent();
        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerIsNotAlreadyConsuming() throws Exception
    {
        disruptorWizard.after(createDelayedEventHandler()).handleEventsWith(createDelayedEventHandler());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerUsedWithAndIsNotAlreadyConsuming() throws Exception
    {
        final DelayedEventHandler handler1 = createDelayedEventHandler();
        final DelayedEventHandler handler2 = createDelayedEventHandler();
        disruptorWizard.handleEventsWith(handler1);
        disruptorWizard.after(handler1).and(handler2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldTrackEventHandlersByIdentityNotEquality() throws Exception
    {
        EvilEqualsEventHandler handler1 = new EvilEqualsEventHandler();
        EvilEqualsEventHandler handler2 = new EvilEqualsEventHandler();

        disruptorWizard.handleEventsWith(handler1);
        disruptorWizard.after(handler2); // handler2.equals(handler1) but it hasn't yet been registered so should throw exception.
    }

    @Test
    public void shouldSupportSpecifyingADefaultExceptionHandlerForEventProcessors() throws Exception
    {
        AtomicReference<Exception> eventHandled = new AtomicReference<Exception>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptorWizard.handleExceptionsWith(exceptionHandler);
        disruptorWizard.handleEventsWith(handler);

        publishEvent();

        final Exception actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test
    public void shouldBlockProducerUntilAllEventProcessorsHaveAdvanced() throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptorWizard.handleEventsWith(delayedEventHandler);

        final RingBuffer<TestEvent> ringBuffer = disruptorWizard.start();

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
        disruptorWizard.handleEventsWith(eventHandler).then(eventHandler2);

        publishEvent();

        AtomicReference<Exception> reference = new AtomicReference<Exception>();
        StubExceptionHandler exceptionHandler = new StubExceptionHandler(reference);
        disruptorWizard.handleExceptionsFor(eventHandler2).with(exceptionHandler);
        eventHandler.processEvent();

        waitFor(reference);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenAddingEventProcessorsAfterTheProducerBarrierHasBeenCreated() throws Exception
    {
        executor.ignoreExecutions();
        disruptorWizard.handleEventsWith(new NoOpEventHandler());
        disruptorWizard.start();
        disruptorWizard.handleEventsWith(new NoOpEventHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfStartIsCalledTwice() throws Exception
    {
        executor.ignoreExecutions();
        disruptorWizard.handleEventsWith(new NoOpEventHandler());
        disruptorWizard.start();
        disruptorWizard.start();
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
        disruptorWizard = new DisruptorWizard<TestEvent>(TestEvent.EVENT_FACTORY, 4, executor,
                                                         ClaimStrategy.Option.SINGLE_THREADED,
                                                         WaitStrategy.Option.BLOCKING);
    }

    private TestEvent publishEvent()
    {
        if (ringBuffer == null)
        {
            ringBuffer = disruptorWizard.start();
        }

        final long sequence = ringBuffer.nextSequence();
        final TestEvent stubEntry = ringBuffer.get(sequence);
        ringBuffer.publish(sequence);
        return stubEntry;
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
}
