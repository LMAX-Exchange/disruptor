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

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.stubs.DelayedEventHandler;
import com.lmax.disruptor.dsl.stubs.EventHandlerStub;
import com.lmax.disruptor.dsl.stubs.EvilEqualsEventHandler;
import com.lmax.disruptor.dsl.stubs.ExceptionThrowingEventHandler;
import com.lmax.disruptor.dsl.stubs.SleepingEventHandler;
import com.lmax.disruptor.dsl.stubs.StubExceptionHandler;
import com.lmax.disruptor.dsl.stubs.StubExecutor;
import com.lmax.disruptor.dsl.stubs.StubPublisher;
import com.lmax.disruptor.dsl.stubs.TestWorkHandler;
import com.lmax.disruptor.support.TestEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Thread.yield;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

@SuppressWarnings(value = {"unchecked"})
public class DisruptorTest
{
    private static final int TIMEOUT_IN_SECONDS = 2;
    private Disruptor<TestEvent> disruptor;
    private StubExecutor executor;
    private final Collection<DelayedEventHandler> delayedEventHandlers = new ArrayList<DelayedEventHandler>();
    private final Collection<TestWorkHandler> testWorkHandlers = new ArrayList<TestWorkHandler>();
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
        for (TestWorkHandler testWorkHandler : testWorkHandlers)
        {
            testWorkHandler.stopWaiting();
        }

        disruptor.halt();
        executor.joinAllThreads();
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
        assertThat(Integer.valueOf(executor.getExecutionCount()), equalTo(Integer.valueOf(2)));
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstHandlersImmediately() throws Exception
    {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler = new EventHandlerStub<TestEvent>(countDownLatch);

        disruptor.handleEventsWith(createDelayedEventHandler(), eventHandler);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch);
    }

    @Test
    public void shouldWaitUntilAllFirstEventProcessorsProcessEventBeforeMakingItAvailableToDependentEventProcessors()
        throws Exception
    {
        DelayedEventHandler eventHandler1 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub<TestEvent>(countDownLatch);

        disruptor.handleEventsWith(eventHandler1).then(eventHandler2);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, eventHandler1);
    }

    @Test
    public void shouldAllowSpecifyingSpecificEventProcessorsToWaitFor()
        throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<TestEvent>(countDownLatch);

        disruptor.handleEventsWith(handler1, handler2);
        disruptor.after(handler1, handler2).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);
    }

    @Test
    public void shouldWaitOnAllProducersJoinedByAnd()
        throws Exception
    {
        DelayedEventHandler handler1 = createDelayedEventHandler();
        DelayedEventHandler handler2 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<TestEvent>(countDownLatch);

        disruptor.handleEventsWith(handler1);
        final EventHandlerGroup<TestEvent> handler2Group = disruptor.handleEventsWith(handler2);
        disruptor.after(handler1).and(handler2Group).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, handler1, handler2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerIsNotAlreadyConsuming()
        throws Exception
    {
        disruptor.after(createDelayedEventHandler()).handleEventsWith(createDelayedEventHandler());
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldTrackEventHandlersByIdentityNotEquality()
        throws Exception
    {
        EvilEqualsEventHandler handler1 = new EvilEqualsEventHandler();
        EvilEqualsEventHandler handler2 = new EvilEqualsEventHandler();

        disruptor.handleEventsWith(handler1);

        // handler2.equals(handler1) but it hasn't yet been registered so should throw exception.
        disruptor.after(handler2);
    }

    @Test
    public void shouldSupportSpecifyingADefaultExceptionHandlerForEventProcessors()
        throws Exception
    {
        AtomicReference<Throwable> eventHandled = new AtomicReference<Throwable>();
        ExceptionHandler exceptionHandler = new StubExceptionHandler(eventHandled);
        RuntimeException testException = new RuntimeException();
        ExceptionThrowingEventHandler handler = new ExceptionThrowingEventHandler(testException);

        disruptor.handleExceptionsWith(exceptionHandler);
        disruptor.handleEventsWith(handler);

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
    public void shouldBeAbleToOverrideTheExceptionHandlerForAEventProcessor()
        throws Exception
    {
        final RuntimeException testException = new RuntimeException();
        final ExceptionThrowingEventHandler eventHandler = new ExceptionThrowingEventHandler(testException);
        disruptor.handleEventsWith(eventHandler);

        AtomicReference<Throwable> reference = new AtomicReference<Throwable>();
        StubExceptionHandler exceptionHandler = new StubExceptionHandler(reference);
        disruptor.handleExceptionsFor(eventHandler).with(exceptionHandler);

        publishEvent();

        waitFor(reference);
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionWhenAddingEventProcessorsAfterTheProducerBarrierHasBeenCreated()
        throws Exception
    {
        executor.ignoreExecutions();
        disruptor.handleEventsWith(new SleepingEventHandler());
        disruptor.start();
        disruptor.handleEventsWith(new SleepingEventHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfStartIsCalledTwice()
        throws Exception
    {
        executor.ignoreExecutions();
        disruptor.handleEventsWith(new SleepingEventHandler());
        disruptor.start();
        disruptor.start();
    }

    @Test
    public void shouldSupportCustomProcessorsAsDependencies()
        throws Exception
    {
        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();

        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<TestEvent>(countDownLatch);

        final BatchEventProcessor<TestEvent> processor =
            new BatchEventProcessor<TestEvent>(ringBuffer, ringBuffer.newBarrier(), delayedEventHandler);
        disruptor.handleEventsWith(processor);
        disruptor.after(processor).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);
    }

    @Test
    public void shouldSupportHandlersAsDependenciesToCustomProcessors()
        throws Exception
    {
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);


        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<TestEvent>(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor =
            new BatchEventProcessor<TestEvent>(ringBuffer, sequenceBarrier, handlerWithBarrier);
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
        EventHandler<TestEvent> handlerWithBarrier = new EventHandlerStub<TestEvent>(countDownLatch);

        final SequenceBarrier sequenceBarrier = disruptor.after(delayedEventHandler1).asSequenceBarrier();
        final BatchEventProcessor<TestEvent> processor =
            new BatchEventProcessor<TestEvent>(ringBuffer, sequenceBarrier, delayedEventHandler2);

        disruptor.after(delayedEventHandler1).and(processor).handleEventsWith(handlerWithBarrier);

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler1, delayedEventHandler2);
    }

    @Test
    public void shouldProvideEventsToWorkHandlers() throws Exception
    {
        final TestWorkHandler workHandler1 = createTestWorkHandler();
        final TestWorkHandler workHandler2 = createTestWorkHandler();
        disruptor.handleEventsWithWorkerPool(workHandler1, workHandler2);

        publishEvent();
        publishEvent();

        workHandler1.processEvent();
        workHandler2.processEvent();
    }

    @Test
    public void shouldSupportUsingWorkerPoolAsDependency() throws Exception
    {
        final TestWorkHandler workHandler1 = createTestWorkHandler();
        final TestWorkHandler workHandler2 = createTestWorkHandler();
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWithWorkerPool(workHandler1, workHandler2).then(delayedEventHandler);

        publishEvent();
        publishEvent();

        assertThat(disruptor.getBarrierFor(delayedEventHandler).getCursor(), equalTo(-1L));

        workHandler2.processEvent();
        workHandler1.processEvent();

        delayedEventHandler.processEvent();
    }

    @Test
    public void shouldSupportUsingWorkerPoolAsDependencyAndProcessFirstEventAsSoonAsItIsAvailable() throws Exception
    {
        final TestWorkHandler workHandler1 = createTestWorkHandler();
        final TestWorkHandler workHandler2 = createTestWorkHandler();
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWithWorkerPool(workHandler1, workHandler2).then(delayedEventHandler);

        publishEvent();
        publishEvent();

        workHandler1.processEvent();
        delayedEventHandler.processEvent();

        workHandler2.processEvent();
        delayedEventHandler.processEvent();
    }

    @Test
    public void shouldSupportUsingWorkerPoolWithADependency() throws Exception
    {
        final TestWorkHandler workHandler1 = createTestWorkHandler();
        final TestWorkHandler workHandler2 = createTestWorkHandler();
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler).thenHandleEventsWithWorkerPool(workHandler1, workHandler2);

        publishEvent();
        publishEvent();

        delayedEventHandler.processEvent();
        delayedEventHandler.processEvent();

        workHandler1.processEvent();
        workHandler2.processEvent();
    }

    @Test
    public void shouldSupportCombiningWorkerPoolWithEventHandlerAsDependencyWhenNotPreviouslyRegistered() throws Exception
    {
        final TestWorkHandler workHandler1 = createTestWorkHandler();
        final DelayedEventHandler delayedEventHandler1 = createDelayedEventHandler();
        final DelayedEventHandler delayedEventHandler2 = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler1).and(disruptor.handleEventsWithWorkerPool(workHandler1)).then(delayedEventHandler2);

        publishEvent();
        publishEvent();

        delayedEventHandler1.processEvent();
        delayedEventHandler1.processEvent();

        workHandler1.processEvent();
        delayedEventHandler2.processEvent();

        workHandler1.processEvent();
        delayedEventHandler2.processEvent();
    }

    @Test(expected = TimeoutException.class, timeout = 2000)
    public void shouldThrowTimeoutExceptionIfShutdownDoesNotCompleteNormally() throws Exception
    {
        //Given
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        disruptor.handleEventsWith(delayedEventHandler);
        publishEvent();

        //When
        disruptor.shutdown(1, SECONDS);

        //Then
    }

    @Test(timeout = 1000)
    public void shouldTrackRemainingCapacity() throws Exception
    {
        final long[] remainingCapacity = {-1};
        //Given
        final EventHandler<TestEvent> eventHandler = new EventHandler<TestEvent>()
        {
            @Override
            public void onEvent(final TestEvent event, final long sequence, final boolean endOfBatch) throws Exception
            {
                remainingCapacity[0] = disruptor.getRingBuffer().remainingCapacity();
            }
        };

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
        final EventHandler<Object> objectHandler = new EventHandlerStub<Object>(latch);

        disruptor.handleEventsWith(objectHandler);

        ensureTwoEventsProcessedAccordingToDependencies(latch);
    }

    @Test
    public void shouldAllowChainingEventHandlersWithSuperType() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(2);
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();
        final EventHandler<Object> objectHandler = new EventHandlerStub<Object>(latch);

        disruptor.handleEventsWith(delayedEventHandler).then(objectHandler);

        ensureTwoEventsProcessedAccordingToDependencies(latch, delayedEventHandler);
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstCustomProcessorsImmediately() throws Exception
    {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final EventHandler<TestEvent> eventHandler = new EventHandlerStub<TestEvent>(countDownLatch);

        disruptor.handleEventsWith(new EventProcessorFactory<TestEvent>()
        {
            @Override
            public EventProcessor createEventProcessor(final RingBuffer<TestEvent> ringBuffer, final Sequence[] barrierSequences)
            {
                assertEquals("Should not have had any barrier sequences", 0, barrierSequences.length);
                return new BatchEventProcessor<TestEvent>(disruptor.getRingBuffer(), ringBuffer.newBarrier(barrierSequences), eventHandler);
            }
        });

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch);
    }

    @Test
    public void shouldHonourDependenciesForCustomProcessors() throws Exception
    {
        final CountDownLatch countDownLatch = new CountDownLatch(2);
        final EventHandler<TestEvent> eventHandler = new EventHandlerStub<TestEvent>(countDownLatch);
        final DelayedEventHandler delayedEventHandler = createDelayedEventHandler();

        disruptor.handleEventsWith(delayedEventHandler).then(new EventProcessorFactory<TestEvent>()
        {
            @Override
            public EventProcessor createEventProcessor(final RingBuffer<TestEvent> ringBuffer, final Sequence[] barrierSequences)
            {
                assertSame("Should have had a barrier sequence", 1, barrierSequences.length);
                return new BatchEventProcessor<TestEvent>(disruptor.getRingBuffer(), ringBuffer.newBarrier(barrierSequences), eventHandler);
            }
        });

        ensureTwoEventsProcessedAccordingToDependencies(countDownLatch, delayedEventHandler);
    }

    private TestWorkHandler createTestWorkHandler()
    {
        final TestWorkHandler testWorkHandler = new TestWorkHandler();
        testWorkHandlers.add(testWorkHandler);
        return testWorkHandler;
    }

    private void ensureTwoEventsProcessedAccordingToDependencies(final CountDownLatch countDownLatch,
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

    private void assertProducerReaches(final StubPublisher stubPublisher,
                                       final int expectedPublicationCount,
                                       boolean strict)
    {
        long loopStart = System.currentTimeMillis();
        while (stubPublisher.getPublicationCount() < expectedPublicationCount && System.currentTimeMillis() - loopStart < 5000)
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
            assertTrue("Producer reached unexpected count. Expected at least " + expectedPublicationCount +
                       " but only reached " + actualPublicationCount, actualPublicationCount >= expectedPublicationCount);
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
                                             ProducerType.SINGLE, new BlockingWaitStrategy());
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

        disruptor.publishEvent(new EventTranslator<TestEvent>()
        {
            @Override
            public void translateTo(final TestEvent event, final long sequence)
            {
                lastPublishedEvent = event;
            }
        });

        return lastPublishedEvent;
    }

    private Throwable waitFor(final AtomicReference<Throwable> reference)
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

    private void assertThatCountDownLatchEquals(final CountDownLatch countDownLatch,
                                                final long expectedCountDownValue)
    {
        assertThat(Long.valueOf(countDownLatch.getCount()), equalTo(Long.valueOf(expectedCountDownValue)));
    }

    private void assertThatCountDownLatchIsZero(final CountDownLatch countDownLatch)
        throws InterruptedException
    {
        boolean released = countDownLatch.await(TIMEOUT_IN_SECONDS, SECONDS);
        assertTrue("Batch handler did not receive entries: " + countDownLatch.getCount(), released);
    }
}
