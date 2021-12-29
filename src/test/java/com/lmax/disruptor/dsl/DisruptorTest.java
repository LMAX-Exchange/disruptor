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

import com.lmax.disruptor.processor.BatchEventProcessor;
import com.lmax.disruptor.strategy.wait.BlockingWaitStrategy;
import com.lmax.disruptor.handler.eventhandler.EventHandler;
import com.lmax.disruptor.handler.exceptionhandler.ExceptionHandler;
import com.lmax.disruptor.handler.exceptionhandler.FatalExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.barrier.SequenceBarrier;
import com.lmax.disruptor.exception.TimeoutException;
import com.lmax.disruptor.dsl.stubs.DelayedEventHandler;
import com.lmax.disruptor.dsl.stubs.EventHandlerStub;
import com.lmax.disruptor.dsl.stubs.EvilEqualsEventHandler;
import com.lmax.disruptor.dsl.stubs.ExceptionThrowingEventHandler;
import com.lmax.disruptor.dsl.stubs.SleepingEventHandler;
import com.lmax.disruptor.dsl.stubs.StubExceptionHandler;
import com.lmax.disruptor.dsl.stubs.StubPublisher;
import com.lmax.disruptor.dsl.stubs.StubThreadFactory;
import com.lmax.disruptor.support.TestEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@SuppressWarnings(value = {"unchecked"})
public class DisruptorTest
{
    private static final int TIMEOUT_IN_SECONDS = 2;

    public final StubThreadFactory executor = new StubThreadFactory();

    private final Collection<DelayedEventHandler> delayedEventHandlers = new ArrayList<>();
    private Disruptor<TestEvent> disruptor;
    private RingBuffer<TestEvent> ringBuffer;
    private TestEvent lastPublishedEvent;

    @BeforeEach
    public void setUp() throws Exception
    {
        createDisruptor();
    }

    @AfterEach
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
    public void shouldHaveStartedAfterStartCalled() throws Exception
    {
        assertFalse(disruptor.hasStarted(), "Should only be set to started after start is called");

        disruptor.start();

        assertTrue(disruptor.hasStarted(), "Should be set to started after start is called");
    }

    @Test
    public void shouldProcessMessagesPublishedBeforeStartIsCalled() throws Exception
    {
        final CountDownLatch eventCounter = new CountDownLatch(2);
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> eventCounter.countDown());

        disruptor.publishEvent((event, sequence) -> lastPublishedEvent = event);

        disruptor.start();

        disruptor.publishEvent((event, sequence) -> lastPublishedEvent = event);

        if (!eventCounter.await(5, TimeUnit.SECONDS))
        {
            fail("Did not process event published before start was called. Missed events: " + eventCounter.getCount());
        }
    }


    @Test
    public void shouldBatchOfEvents() throws Exception
    {
        final CountDownLatch eventCounter = new CountDownLatch(2);
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> eventCounter.countDown());

        disruptor.start();

        disruptor.publishEvents((event, sequence, arg) -> lastPublishedEvent = event, new Object[] { "a", "b" });

        if (!eventCounter.await(5, TimeUnit.SECONDS))
        {
            fail("Did not process event published before start was called. Missed events: " + eventCounter.getCount());
        }
    }

    @Test
    public void shouldAddEventProcessorsAfterPublishing() throws Exception
    {
        RingBuffer<TestEvent> rb = disruptor.getRingBuffer();
        BatchEventProcessor<TestEvent> b1 = new BatchEventProcessor<>(
                rb, rb.newBarrier(), new SleepingEventHandler());
        BatchEventProcessor<TestEvent> b2 = new BatchEventProcessor<>(
                rb, rb.newBarrier(b1.getSequence()), new SleepingEventHandler());
        BatchEventProcessor<TestEvent> b3 = new BatchEventProcessor<>(
                rb, rb.newBarrier(b2.getSequence()), new SleepingEventHandler());

        assertThat(b1.getSequence().get(), is(-1L));
        assertThat(b2.getSequence().get(), is(-1L));
        assertThat(b3.getSequence().get(), is(-1L));

        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());

        disruptor.handleEventsWith(b1, b2, b3);

        assertThat(b1.getSequence().get(), is(5L));
        assertThat(b2.getSequence().get(), is(5L));
        assertThat(b3.getSequence().get(), is(5L));
    }

    @Test
    public void shouldSetSequenceForHandlerIfAddedAfterPublish() throws Exception
    {
        RingBuffer<TestEvent> rb = disruptor.getRingBuffer();
        EventHandler<TestEvent> b1 = new SleepingEventHandler();
        EventHandler<TestEvent> b2 = new SleepingEventHandler();
        EventHandler<TestEvent> b3 = new SleepingEventHandler();

        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());
        rb.publish(rb.next());

        disruptor.handleEventsWith(b1, b2, b3);

        assertThat(disruptor.getSequenceValueFor(b1), is(5L));
        assertThat(disruptor.getSequenceValueFor(b2), is(5L));
        assertThat(disruptor.getSequenceValueFor(b3), is(5L));
    }

    @Test
    public void shouldCreateEventProcessorGroupForFirstEventProcessors()
        throws Exception
    {
        executor.ignoreExecutions();
        final EventHandler<TestEvent> eventHandler1 = new SleepingEventHandler();
        EventHandler<TestEvent> eventHandler2 = new SleepingEventHandler();

        final EventHandlerGroup<TestEvent> eventHandlerGroup =
            disruptor.handleEventsWith(eventHandler1, eventHandler2);
        disruptor.start();

        assertNotNull(eventHandlerGroup);
        assertThat(executor.getExecutionCount(), equalTo(2));
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstHandlersImmediately() throws Exception
    {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler = new EventHandlerStub<>(countDownLatch);

        disruptor.handleEventsWith(createDelayedEventHandler(), eventHandler);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch);
    }

    @Test
    public void shouldWaitUntilAllFirstEventProcessorsProcessEventBeforeMakingItAvailableToDependentEventProcessors()
        throws Exception
    {
        DelayedEventHandler eventHandler1 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub<>(countDownLatch);

        disruptor.handleEventsWith(eventHandler1).then(eventHandler2);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, eventHandler1);
    }

    @Test
    public void shouldSupportAddingCustomEventProcessorWithFactory()
        throws Exception
    {
        RingBuffer<TestEvent> rb = disruptor.getRingBuffer();
        BatchEventProcessor<TestEvent> b1 = new BatchEventProcessor<>(
                rb, rb.newBarrier(), new SleepingEventHandler());
        EventProcessorFactory<TestEvent> b2 = (ringBuffer, barrierSequences) -> new BatchEventProcessor<>(
                ringBuffer, ringBuffer.newBarrier(barrierSequences), new SleepingEventHandler());

        disruptor.handleEventsWith(b1).then(b2);

        disruptor.start();

        assertThat(executor.getExecutionCount(), equalTo(2));
    }

    @Test
    public void shouldAllowSpecifyingSpecificEventProcessorsToWaitFor()
        throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        disruptor.handleEventsWith(handler1, handler2);
        disruptor.after(handler1, handler2).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);

        assertThat(executor.getExecutionCount(), equalTo(3));
    }

    @Test
    public void shouldWaitOnAllProducersJoinedByAnd()
        throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        disruptor.handleEventsWith(handler1);
        final EventHandlerGroup<TestEvent> handler2Group = disruptor.handleEventsWith(handler2);
        disruptor.after(handler1).and(handler2Group).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);

        assertThat(executor.getExecutionCount(), equalTo(3));
    }

    @Test
    public void shouldThrowExceptionIfHandlerIsNotAlreadyConsuming()
        throws Exception
    {
        assertThrows(IllegalArgumentException.class, () ->
                disruptor.after(createDelayedEventHandler()).handleEventsWith(createDelayedEventHandler()));
    }

    @Test
    public void shouldTrackEventHandlersByIdentityNotEquality()
        throws Exception
    {
        assertThrows(IllegalArgumentException.class, () ->
        {
            EvilEqualsEventHandler handler1 = new EvilEqualsEventHandler();
            EvilEqualsEventHandler handler2 = new EvilEqualsEventHandler();

            disruptor.handleEventsWith(handler1);

            // handler2.equals(handler1) but it hasn't yet been registered so should throw exception.
            disruptor.after(handler2);
        });
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldSupportSpecifyingAExceptionHandlerForEventProcessors()
        throws Exception
    {
        AtomicReference<Throwable> eventHandled = new AtomicReference<>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.handleExceptionsWith(exceptionHandler);
        disruptor.handleEventsWith(handler);

        publishEvent();

        final Throwable actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldOnlyApplyExceptionsHandlersSpecifiedViaHandleExceptionsWithOnNewEventProcessors()
        throws Exception
    {
        AtomicReference<Throwable> eventHandled = new AtomicReference<>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.handleExceptionsWith(exceptionHandler);
        disruptor.handleEventsWith(handler);
        disruptor.handleExceptionsWith(new FatalExceptionHandler());

        publishEvent();

        final Throwable actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test
    public void shouldSupportSpecifyingADefaultExceptionHandlerForEventProcessors()
        throws Exception
    {
        AtomicReference<Throwable> eventHandled = new AtomicReference<>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.setDefaultExceptionHandler(exceptionHandler);
        disruptor.handleEventsWith(handler);

        publishEvent();

        final Throwable actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test
    public void shouldApplyDefaultExceptionHandlerToExistingEventProcessors()
        throws Exception
    {
        AtomicReference<Throwable> eventHandled = new AtomicReference<>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.handleEventsWith(handler);
        disruptor.setDefaultExceptionHandler(exceptionHandler);

        publishEvent();

        final Throwable actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test
    public void shouldBlockProducerUntilAllEventProcessorsHaveAdvanced()
        throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);

        final RingBuffer<TestEvent> ringBuffer = disruptor.start();
        delayedEventHandler.awaitStart();

        final StubPublisher stubPublisher = new StubPublisher(ringBuffer);
        try
        {
            executor.newThread(stubPublisher).start();

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
    public void shouldBeAbleToOverrideTheExceptionHandlerForAEventProcessor()
        throws Exception
    {
        final RuntimeException testException = new RuntimeException();
        final ExceptionThrowingEventHandler eventHandler = new ExceptionThrowingEventHandler(testException);
        disruptor.handleEventsWith(eventHandler);

        AtomicReference<Throwable> reference = new AtomicReference<>();
        StubExceptionHandler exceptionHandler = new StubExceptionHandler(reference);
        disruptor.handleExceptionsFor(eventHandler).with(exceptionHandler);

        publishEvent();

        waitFor(reference);
    }

    @Test
    public void shouldThrowExceptionWhenAddingEventProcessorsAfterTheProducerBarrierHasBeenCreated()
        throws Exception
    {
        assertThrows(IllegalStateException.class, () ->
        {
            executor.ignoreExecutions();
            disruptor.handleEventsWith(new SleepingEventHandler());
            disruptor.start();
            disruptor.handleEventsWith(new SleepingEventHandler());
        });
    }

    @Test
    public void shouldThrowExceptionIfStartIsCalledTwice()
        throws Exception
    {
        assertThrows(IllegalStateException.class, () ->
        {
            executor.ignoreExecutions();
            disruptor.handleEventsWith(new SleepingEventHandler());
            disruptor.start();
            disruptor.start();
        });
    }

    @Test
    public void shouldSupportCustomProcessorsAsDependencies()
        throws Exception
    {
        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();

        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        final BatchEventProcessor<TestEvent> processor =
                new BatchEventProcessor<>(ringBuffer, ringBuffer.newBarrier(), delayedEventHandler);

        disruptor.handleEventsWith(processor).then(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);

        assertThat(executor.getExecutionCount(), equalTo(2));
    }

    @Test
    public void shouldSupportHandlersAsDependenciesToCustomProcessors()
        throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);


        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor =
                new BatchEventProcessor<>(ringBuffer, sequenceBarrier, handlerWithBarrier);
        disruptor.handleEventsWith(processor);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);

        assertThat(executor.getExecutionCount(), equalTo(2));
    }

    @Test
    public void shouldSupportCustomProcessorsAndHandlersAsDependencies() throws Exception
    {
        final DelayedEventHandler delayedEventHandler1 = createDelayedEventHandler();
        final DelayedEventHandler delayedEventHandler2 = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler1);


        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler1).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor =
                new BatchEventProcessor<>(ringBuffer, sequenceBarrier, delayedEventHandler2);

        disruptor.after(delayedEventHandler1).and(processor).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler1, delayedEventHandler2);

        assertThat(executor.getExecutionCount(), equalTo(3));
    }

    @Test
    public void shouldSupportMultipleCustomProcessorsAsDependencies() throws Exception
    {
        final RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<>(countDownLatch);

        final DelayedEventHandler delayedEventHandler1 = createDelayedEventHandler();
        final BatchEventProcessor<TestEvent> processor1 =
                new BatchEventProcessor<>(ringBuffer, ringBuffer.newBarrier(), delayedEventHandler1);

        final DelayedEventHandler delayedEventHandler2 = createDelayedEventHandler();
        final BatchEventProcessor<TestEvent> processor2 =
                new BatchEventProcessor<>(ringBuffer, ringBuffer.newBarrier(), delayedEventHandler2);

        disruptor.handleEventsWith(processor1, processor2);
        disruptor.after(processor1, processor2).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler1, delayedEventHandler2);
        assertThat(executor.getExecutionCount(), equalTo(3));
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void shouldThrowTimeoutExceptionIfShutdownDoesNotCompleteNormally() throws Exception
    {
        assertThrows(TimeoutException.class, () ->
        {
            //Given
            final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
            disruptor.handleEventsWith(delayedEventHandler);
            publishEvent();
            //Then
            disruptor.shutdown(1, SECONDS);
        });
    }

    @Test
    @Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
    public void shouldTrackRemainingCapacity() throws Exception
    {
        final long[] remainingCapacity = {-1};
        //Given
        final EventHandler<TestEvent> eventHandler = (event, sequence, endOfBatch) ->
                remainingCapacity[0] = disruptor.getRingBuffer().remainingCapacity();

        disruptor.handleEventsWith(eventHandler);

        //When
        publishEvent();

        //Then
        while (remainingCapacity[0] == -1)
        {
            Thread.sleep(100);
        }
        assertThat(remainingCapacity[0], is(ringBuffer.getBufferSize() - 1L));
        assertThat(disruptor.getRingBuffer().remainingCapacity(), is(ringBuffer.getBufferSize() - 0L));
    }

    @Test
    public void shouldAllowEventHandlerWithSuperType() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(2);
        final EventHandler<Object> objectHandler = new EventHandlerStub<>(latch);

        disruptor.handleEventsWith(objectHandler);

        ensureTwoEventsProcessedAccordingToDependencies(latch);
    }

    @Test
    public void shouldAllowChainingEventHandlersWithSuperType() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(2);
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        final EventHandler<Object> objectHandler = new EventHandlerStub<>(latch);

        disruptor.handleEventsWith(delayedEventHandler).then(objectHandler);

        ensureTwoEventsProcessedAccordingToDependencies(latch, delayedEventHandler);
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstCustomProcessorsImmediately() throws Exception
    {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final EventHandler<TestEvent> eventHandler = new EventHandlerStub<>(countDownLatch);

        disruptor.handleEventsWith(
                (ringBuffer, barrierSequences) ->
                {
                    assertEquals(0, barrierSequences.length,
                            "Should not have had any barrier sequences");
                    return new BatchEventProcessor<>(
                            disruptor.getRingBuffer(), ringBuffer.newBarrier(
                            barrierSequences), eventHandler);
                });

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch);
    }

    @Test
    public void shouldHonourDependenciesForCustomProcessors() throws Exception
    {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final EventHandler<TestEvent> eventHandler = new EventHandlerStub<>(countDownLatch);
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();

        disruptor.handleEventsWith(delayedEventHandler).then(
                (ringBuffer, barrierSequences) ->
                {
                    assertSame(1, barrierSequences.length, "Should have had a barrier sequence");
                    return new BatchEventProcessor<>(
                            disruptor.getRingBuffer(), ringBuffer.newBarrier(
                            barrierSequences), eventHandler);
                });

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);
    }

    private void ensureTwoEventsProcessedAccordingToDependencies(
        final CountDownLatch countDownLatch,
        final DelayedEventHandler... dependencies)
        throws InterruptedException, BrokenBarrierException
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

    private void assertProducerReaches(
        final StubPublisher stubPublisher,
        final int expectedPublicationCount,
        final boolean strict)
    {
        long loopStart = System.currentTimeMillis();
        while (stubPublisher.getPublicationCount() < expectedPublicationCount && System
            .currentTimeMillis() - loopStart < 5000)
        {
            Thread.yield();
        }

        if (strict)
        {
            assertThat(stubPublisher.getPublicationCount(), equalTo(expectedPublicationCount));
        }
        else
        {
            final int actualPublicationCount = stubPublisher.getPublicationCount();
            final String msg = "Producer reached unexpected count. Expected at least " + expectedPublicationCount +
                    " but only reached " + actualPublicationCount;
            assertTrue(actualPublicationCount >= expectedPublicationCount, msg);
        }
    }

    private void createDisruptor()
    {
        disruptor = new Disruptor<>(
                TestEvent.EVENT_FACTORY,
                4,
                executor,
                ProducerType.SINGLE,
                new BlockingWaitStrategy());
    }

    private TestEvent publishEvent() throws InterruptedException, BrokenBarrierException
    {
        if (ringBuffer == null)
        {
            ringBuffer = disruptor.start();

            for (DelayedEventHandler eventHandler : delayedEventHandlers)
            {
                eventHandler.awaitStart();
            }
        }

        disruptor.publishEvent((event, sequence) -> lastPublishedEvent = event);

        return lastPublishedEvent;
    }

    private Throwable waitFor(final AtomicReference<Throwable> reference)
    {
        while (reference.get() == null)
        {
            Thread.yield();
        }

        return reference.get();
    }

    private DelayedEventHandler createDelayedEventHandler()
    {
        final DelayedEventHandler delayedEventHandler = new DelayedEventHandler();
        delayedEventHandlers.add(delayedEventHandler);
        return delayedEventHandler;
    }

    private void assertThatCountDownLatchEquals(
        final CountDownLatch countDownLatch,
        final long expectedCountDownValue)
    {
        assertThat(Long.valueOf(countDownLatch.getCount()), equalTo(Long.valueOf(expectedCountDownValue)));
    }

    private void assertThatCountDownLatchIsZero(final CountDownLatch countDownLatch)
        throws InterruptedException
    {
        boolean released = countDownLatch.await(TIMEOUT_IN_SECONDS, SECONDS);
        assertTrue(released, "Batch handler did not receive entries: " + countDownLatch.getCount());
    }
}
