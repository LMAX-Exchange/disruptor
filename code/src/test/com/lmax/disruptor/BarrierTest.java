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

import static com.lmax.disruptor.support.Actions.countDown;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.support.StubEvent;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.action.DoAllAction;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(JMock.class)
public final class BarrierTest
{
    private Mockery context = new Mockery();
    private RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 64);
    private EventProcessor eventProcessor1 = context.mock(EventProcessor.class, "ep1");
    private EventProcessor eventProcessor2 = context.mock(EventProcessor.class, "ep2");
    private EventProcessor eventProcessor3 = context.mock(EventProcessor.class, "ep3");
    private Barrier<StubEvent> barrier =
        ringBuffer.createBarrier(eventProcessor1, eventProcessor2, eventProcessor3);

    {
        ringBuffer.setTrackedProcessors(new NoOpEventProcessor(ringBuffer));
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsAhead() throws Exception
    {
        final long expectedNumberMessages = 10;
        final long expectedWorkSequence = 9;
        fillRingBuffer(expectedNumberMessages);

        context.checking(new Expectations()
        {
            {
                one(eventProcessor1).getSequence();
                will(returnValue(Long.valueOf(expectedNumberMessages)));

                one(eventProcessor2).getSequence();
                will(returnValue(Long.valueOf(expectedWorkSequence)));

                one(eventProcessor3).getSequence();
                will(returnValue(Long.valueOf(expectedWorkSequence)));
            }
        });

        long completedWorkSequence = barrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereAllWorkersAreBlockedOnRingBuffer() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubEventProcessor[] workers = new StubEventProcessor[3];
        for (int i = 0, size = workers.length; i < size; i++)
        {
            workers[i] = new StubEventProcessor();
            workers[i].setSequence(expectedNumberMessages - 1);
        }

        final Barrier barrier = ringBuffer.createBarrier(workers);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                StubEvent event = ringBuffer.nextEvent();
                event.setValue((int) event.getSequence());
                ringBuffer.publish(event);

                for (StubEventProcessor stubWorker : workers)
                {
                    stubWorker.setSequence(event.getSequence());
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages;
        long completedWorkSequence = barrier.waitFor(expectedNumberMessages);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldInterruptDuringBusySpin() throws Exception
    {
        final long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);
        final CountDownLatch latch = new CountDownLatch(9);

        context.checking(new Expectations()
        {
            {
                allowing(eventProcessor1).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(Long.valueOf(8L))));

                allowing(eventProcessor2).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(Long.valueOf(8L))));

                allowing(eventProcessor3).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(Long.valueOf(8L))));
            }
        });

        final boolean[] alerted = { false };
        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    barrier.waitFor(expectedNumberMessages - 1);
                }
                catch (AlertException e)
                {
                    alerted[0] = true;
                }
                catch (InterruptedException e)
                {
                    // don't care
                }
            }
        });

        t.start();
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        barrier.alert();
        t.join();

        assertTrue("Thread was not interrupted", alerted[0]);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsBehind() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubEventProcessor[] eventProcessors = new StubEventProcessor[3];
        for (int i = 0, size = eventProcessors.length; i < size; i++)
        {
            eventProcessors[i] = new StubEventProcessor();
            eventProcessors[i].setSequence(expectedNumberMessages - 2);
        }

        final Barrier barrier = ringBuffer.createBarrier(eventProcessors);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (StubEventProcessor stubWorker : eventProcessors)
                {
                    stubWorker.setSequence(stubWorker.getSequence() + 1);
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages - 1;
        long completedWorkSequence = barrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldSetAndClearAlertStatus()
    {
        assertFalse(barrier.isAlerted());

        barrier.alert();
        assertTrue(barrier.isAlerted());

        barrier.clearAlert();
        assertFalse(barrier.isAlerted());
    }

    private void fillRingBuffer(long expectedNumberMessages) throws InterruptedException
    {
        for (long i = 0; i < expectedNumberMessages; i++)
        {
            StubEvent event = ringBuffer.nextEvent();
            event.setValue((int) i);
            ringBuffer.publish(event);
        }
    }

    private static final class StubEventProcessor implements EventProcessor
    {
        private volatile long sequence;

        public void setSequence(long sequence)
        {
            this.sequence = sequence;
        }

        @Override
        public long getSequence()
        {
            return sequence;
        }

        @Override
        public void halt()
        {
        }

        @Override
        public void run()
        {
        }
    }
}
