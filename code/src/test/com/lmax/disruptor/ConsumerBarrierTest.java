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

import com.lmax.disruptor.support.StubEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.action.DoAllAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(JMock.class)
public final class ConsumerBarrierTest
{
    private Mockery context;
    private RingBuffer<StubEntry> ringBuffer;
    private Consumer consumer1;
    private Consumer consumer2;
    private Consumer consumer3;
    private ConsumerBarrier<StubEntry> consumerBarrier;
    private ProducerBarrier<StubEntry> producerBarrier;

    @Before
    public void setUp()
    {
        context = new Mockery();

        ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 64);

        consumer1 = context.mock(Consumer.class, "consumer1");
        consumer2 = context.mock(Consumer.class, "consumer2");
        consumer3 = context.mock(Consumer.class, "consumer3");

        consumerBarrier = ringBuffer.createConsumerBarrier(consumer1, consumer2, consumer3);
        producerBarrier = ringBuffer.createProducerBarrier(new NoOpConsumer(ringBuffer));
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
                one(consumer1).getSequence();
                will(returnValue(Long.valueOf(expectedNumberMessages)));

                one(consumer2).getSequence();
                will(returnValue(Long.valueOf(expectedWorkSequence)));

                one(consumer3).getSequence();
                will(returnValue(Long.valueOf(expectedWorkSequence)));
            }
        });

        long completedWorkSequence = consumerBarrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereAllWorkersAreBlockedOnRingBuffer() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubConsumer[] workers = new StubConsumer[3];
        for (int i = 0, size = workers.length; i < size; i++)
        {
            workers[i] = new StubConsumer();
            workers[i].setSequence(expectedNumberMessages - 1);
        }

        final ConsumerBarrier consumerBarrier = ringBuffer.createConsumerBarrier(workers);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                StubEntry entry = producerBarrier.nextEntry();
                entry.setValue((int) entry.getSequence());
                producerBarrier.commit(entry);

                for (StubConsumer stubWorker : workers)
                {
                    stubWorker.setSequence(entry.getSequence());
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages;
        long completedWorkSequence = consumerBarrier.waitFor(expectedNumberMessages);
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
                allowing(consumer1).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(Long.valueOf(8L))));

                allowing(consumer2).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(Long.valueOf(8L))));

                allowing(consumer3).getSequence();
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
                    consumerBarrier.waitFor(expectedNumberMessages - 1);
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
        consumerBarrier.alert();
        t.join();

        assertTrue("Thread was not interrupted", alerted[0]);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsBehind() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubConsumer[] entryConsumers = new StubConsumer[3];
        for (int i = 0, size = entryConsumers.length; i < size; i++)
        {
            entryConsumers[i] = new StubConsumer();
            entryConsumers[i].setSequence(expectedNumberMessages - 2);
        }

        final ConsumerBarrier consumerBarrier = ringBuffer.createConsumerBarrier(entryConsumers);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (StubConsumer stubWorker : entryConsumers)
                {
                    stubWorker.setSequence(stubWorker.getSequence() + 1);
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages - 1;
        long completedWorkSequence = consumerBarrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldSetAndClearAlertStatus()
    {
        assertFalse(consumerBarrier.isAlerted());

        consumerBarrier.alert();
        assertTrue(consumerBarrier.isAlerted());

        consumerBarrier.clearAlert();
        assertFalse(consumerBarrier.isAlerted());
    }

    private void fillRingBuffer(long expectedNumberMessages) throws InterruptedException
    {
        for (long i = 0; i < expectedNumberMessages; i++)
        {
            StubEntry entry = producerBarrier.nextEntry();
            entry.setValue((int)i);
            producerBarrier.commit(entry);
        }
    }

    private static final class StubConsumer implements Consumer
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
