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

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.*;

@SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
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
        executor.stopAll();
    }

    @Test
    @SuppressWarnings({"unchecked"})
    public void shouldCreateEventProcessorGroupForFirstEventProcessors() throws Exception
    {
        executor.ignoreExecutions();
        final EventHandler<TestEvent> eventHandler1 = new DoNothingEventHandler();
        EventHandler<TestEvent> eventHandler2 = new DoNothingEventHandler();

        final EventHandlerGroup eventHandlerGroup = disruptorWizard.consumeWith(eventHandler1, eventHandler2);
        disruptorWizard.start();

        assertNotNull(eventHandlerGroup);
        assertThat(executor.getExecutionCount(), equalTo(2));
    }

    @Test
    public void shouldMakeEntriesAvailableToFirstHandlersImmediately() throws Exception
    {
        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub(countDownLatch);

        disruptorWizard.consumeWith(createDelayedEventHandler(), eventHandler2);

        produceEntry();
        produceEntry();

        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test
    public void shouldWaitUntilAllFirstEventProcessorsProcessEventBeforeMakingItAvailableToDependentEventProcessors() throws Exception
    {
        DelayedEventHandler eventHandler1 = createDelayedEventHandler();

        CountDownLatch countDownLatch = new CountDownLatch(2);
        EventHandler<TestEvent> eventHandler2 = new EventHandlerStub(countDownLatch);

        disruptorWizard.consumeWith(eventHandler1).then(eventHandler2);

        produceEntry();
        produceEntry();

        assertThat(countDownLatch.getCount(), equalTo(2L));

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

        disruptorWizard.consumeWith(handler1, handler2);
        disruptorWizard.after(handler1, handler2).consumeWith(handlerWithBarrier);


        produceEntry();
        produceEntry();

        assertThat(countDownLatch.getCount(), equalTo(2L));

        handler1.processEvent();
        handler2.processEvent();

        assertThat(countDownLatch.getCount(), equalTo(2L));

        handler2.processEvent();
        handler1.processEvent();
        assertTrue("Batch handler did not receive entries.", countDownLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldTrackEventHandlersByIdentityNotEquality() throws Exception
    {
        EvilEqualsEventHandler handler1 = new EvilEqualsEventHandler();
        EvilEqualsEventHandler handler2 = new EvilEqualsEventHandler();

        disruptorWizard.consumeWith(handler1);
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
        disruptorWizard.consumeWith(handler);

        produceEntry();

        final Exception actualException = waitFor(eventHandled);
        assertSame(testException, actualException);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionIfHandlerIsNotAlreadyConsuming() throws Exception
    {
        disruptorWizard.after(createDelayedEventHandler()).consumeWith(createDelayedEventHandler());
    }

    @Test
    public void shouldBlockProducerUntilAllEventProcessorsHaveAdvanced() throws Exception
    {
        final DelayedEventHandler handler1 = createDelayedEventHandler();
        disruptorWizard.consumeWith(handler1);

        final RingBuffer<TestEvent> producerBarrier = disruptorWizard.start();

        final StubProducer stubProducer = new StubProducer(producerBarrier);
        try
        {
            executor.execute(stubProducer);

            assertProducerReaches(stubProducer, 4, true);

            handler1.processEvent();
            handler1.processEvent();
            handler1.processEvent();
            handler1.processEvent();
            handler1.processEvent();

            assertProducerReaches(stubProducer, 5, false);
        }
        finally
        {
            stubProducer.halt();
        }
    }

    @Test
    public void shouldBeAbleToOverrideTheExceptionHandlerForAEventProcessor() throws Exception
    {
        final DelayedEventHandler eventHandler = createDelayedEventHandler();

        final RuntimeException testException = new RuntimeException();
        final ExceptionThrowingEventHandler eventHandler2 = new ExceptionThrowingEventHandler(testException);
        disruptorWizard.consumeWith(eventHandler).then(eventHandler2);

        produceEntry();

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
        disruptorWizard.consumeWith(new DoNothingEventHandler());
        disruptorWizard.start();
        disruptorWizard.consumeWith(new DoNothingEventHandler());
    }

    @Test(expected = IllegalStateException.class)
    public void shouldThrowExceptionIfStartIsCalledTwice() throws Exception
    {
        executor.ignoreExecutions();
        disruptorWizard.consumeWith(new DoNothingEventHandler());
        disruptorWizard.start();
        disruptorWizard.start();
    }

    private void assertProducerReaches(final StubProducer stubProducer, final int productionCount, boolean strict)
    {
        long loopStart = System.currentTimeMillis();
        while (stubProducer.getProductionCount() < productionCount && System.currentTimeMillis() - loopStart < 5000)
        {
            yield();
        }
        if (strict)
        {
            assertThat(stubProducer.getProductionCount(), equalTo(productionCount));
        }
        else
        {
            assertTrue("Producer reached expected count.", stubProducer.getProductionCount() > productionCount);
        }
    }

    private void createDisruptor()
    {
        executor = new StubExecutor();
        createDisruptor(executor);
    }

    private void createDisruptor(final Executor executor)
    {
        disruptorWizard = new DisruptorWizard<TestEvent>(TestEvent.EVENT_FACTORY, 4, executor, ClaimStrategy.Option.SINGLE_THREADED, WaitStrategy.Option.BLOCKING);
    }

    private TestEvent produceEntry()
    {
        if (ringBuffer == null)
        {
            ringBuffer = disruptorWizard.start();
        }
        final TestEvent stubEntry = ringBuffer.nextEvent();
        ringBuffer.publish(stubEntry);
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

    private void yield()
    {
        Thread.yield();
//        try
//        {
//            Thread.sleep(500);
//        }
//        catch (InterruptedException e)
//        {
//            e.printStackTrace();
//        }
    }

    private DelayedEventHandler createDelayedEventHandler()
    {
        final DelayedEventHandler delayedEventHandler = new DelayedEventHandler();
        delayedEventHandlers.add(delayedEventHandler);
        return delayedEventHandler;
    }
}
