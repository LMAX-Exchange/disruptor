package com.lmax.disruptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.support.StubEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.lib.action.CustomAction;
import org.jmock.lib.action.DoAllAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(JMock.class)
public class ThresholdBarrierTest
{
    private Mockery mockery;
    private RingBuffer<StubEntry> ringBuffer;
    private EventConsumer eventProcessor1;
    private EventConsumer eventProcessor2;
    private EventConsumer eventProcessor3;
    private ThresholdBarrier<StubEntry> thresholdBarrier;

    @Before
    public void setUp()
    {
        mockery = new Mockery();

        ringBuffer = new RingBuffer<StubEntry>(StubEntry.FACTORY, 20);
        eventProcessor1 = mockery.mock(EventConsumer.class, "eventConsumer1");
        eventProcessor2 = mockery.mock(EventConsumer.class, "eventConsumer2");
        eventProcessor3 = mockery.mock(EventConsumer.class, "eventConsumer3");
        thresholdBarrier = ringBuffer.createBarrier(eventProcessor1, eventProcessor2, eventProcessor3);
    }

    @Test
    public void shouldGetMinOffWorkers() throws Exception
    {
        final long expectedMinimum = 3;
        mockery.checking(new Expectations()
        {
            {
                one(eventProcessor1).getSequence();
                will(returnValue(expectedMinimum));

                one(eventProcessor2).getSequence();
                will(returnValue(86L));

                one(eventProcessor3).getSequence();
                will(returnValue(2384378L));
            }
        });

        ringBuffer.claimSequence(2384378L).commit();

        assertEquals(expectedMinimum, thresholdBarrier.getProcessedEventSequence());
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsAhead() throws Exception
    {
        final long expectedNumberMessages = 10;
        final long expectedWorkSequence = 9;
        fillRingBuffer(expectedNumberMessages);

        mockery.checking(new Expectations()
        {
            {
                one(eventProcessor1).getSequence();
                will(returnValue(expectedNumberMessages));

                one(eventProcessor2).getSequence();
                will(returnValue(expectedWorkSequence));

                one(eventProcessor3).getSequence();
                will(returnValue(expectedWorkSequence));
            }
        });

        long completedWorkSequence = thresholdBarrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereAllWorkersAreBlockedOnRingBuffer() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubEventConsumer[] workers = new StubEventConsumer[3];
        for (int i = 0, size = workers.length; i < size; i++)
        {
            workers[i] = new StubEventConsumer();
            workers[i].setSequence(expectedNumberMessages - 1);
        }

        final ThresholdBarrier barrier = ringBuffer.createBarrier(workers);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                StubEntry entry = ringBuffer.claimNext();
                entry.setValue((int) entry.getSequence());
                entry.commit();
                for (StubEventConsumer stubWorker : workers)
                {
                    stubWorker.setSequence(entry.getSequence());
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages;
        long completedWorkSequence = barrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldInterruptDuringBusySpin() throws Exception
    {
        final long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);
        final CountDownLatch latch = new CountDownLatch(9);

        mockery.checking(new Expectations()
        {
            {
                allowing(eventProcessor1).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(8L)));

                allowing(eventProcessor2).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(8L)));

                allowing(eventProcessor3).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(8L)));
            }
        });

        final boolean[] alerted = { false };
        Thread t = new Thread(new Runnable()
        {
            public void run()
            {
                try
                {
                    thresholdBarrier.waitFor(expectedNumberMessages - 1);
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
        thresholdBarrier.alert();
        t.join();
        assertTrue("Thread was not interupted", alerted[0]);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsBehind() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubEventConsumer[] eventProcessors = new StubEventConsumer[3];
        for (int i = 0, size = eventProcessors.length; i < size; i++)
        {
            eventProcessors[i] = new StubEventConsumer();
            eventProcessors[i].setSequence(expectedNumberMessages - 2);
        }

        final ThresholdBarrier barrier = ringBuffer.createBarrier(eventProcessors);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (StubEventConsumer stubWorker : eventProcessors)
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

    private void fillRingBuffer(long expectedNumberMessages) throws InterruptedException
    {
        for (long i = 0; i < expectedNumberMessages; i++)
        {
            StubEntry entry = ringBuffer.claimNext();
            entry.setValue((int) i);
            entry.commit();
        }
    }

    private static class StubEventConsumer implements EventConsumer
    {
        private volatile long sequence;

        public void setSequence(long sequence)
        {
            this.sequence = sequence;
        }

        public long getSequence()
        {
            return sequence;
        }

        @Override
        public ThresholdBarrier getBarrier()
        {
            return null;
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

    protected Action countDown(final CountDownLatch latch)
    {
        return new CustomAction("Count Down Latch")
        {
            public Object invoke(Invocation invocation) throws Throwable
            {
                latch.countDown();
                return null;
            }
        };
    }
}
