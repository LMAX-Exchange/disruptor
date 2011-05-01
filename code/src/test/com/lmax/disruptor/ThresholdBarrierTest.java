package com.lmax.disruptor;

import static com.lmax.disruptor.support.Actions.countDown;
import static org.junit.Assert.assertEquals;
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
public final class ThresholdBarrierTest
{
    private Mockery mockery;
    private RingBuffer<StubEntry> ringBuffer;
    private EntryConsumer entryConsumer1;
    private EntryConsumer entryConsumer2;
    private EntryConsumer entryConsumer3;
    private ThresholdBarrier<StubEntry> thresholdBarrier;

    @Before
    public void setUp()
    {
        mockery = new Mockery();

        ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 20);
        entryConsumer1 = mockery.mock(EntryConsumer.class, "entryConsumer1");
        entryConsumer2 = mockery.mock(EntryConsumer.class, "entryConsumer2");
        entryConsumer3 = mockery.mock(EntryConsumer.class, "entryConsumer3");
        thresholdBarrier = ringBuffer.createBarrier(entryConsumer1, entryConsumer2, entryConsumer3);
    }

    @Test
    public void shouldGetMinOffWorkers() throws Exception
    {
        final long expectedMinimum = 3;
        mockery.checking(new Expectations()
        {
            {
                one(entryConsumer1).getSequence();
                will(returnValue(expectedMinimum));

                one(entryConsumer2).getSequence();
                will(returnValue(86L));

                one(entryConsumer3).getSequence();
                will(returnValue(2384378L));
            }
        });

        ringBuffer.claimSequence(2384378L).commit();

        assertEquals(expectedMinimum, thresholdBarrier.getAvailableSequence());
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
                one(entryConsumer1).getSequence();
                will(returnValue(expectedNumberMessages));

                one(entryConsumer2).getSequence();
                will(returnValue(expectedWorkSequence));

                one(entryConsumer3).getSequence();
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

        final StubEntryConsumer[] workers = new StubEntryConsumer[3];
        for (int i = 0, size = workers.length; i < size; i++)
        {
            workers[i] = new StubEntryConsumer();
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

                for (StubEntryConsumer stubWorker : workers)
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
                allowing(entryConsumer1).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(8L)));

                allowing(entryConsumer2).getSequence();
                will(new DoAllAction(countDown(latch), returnValue(8L)));

                allowing(entryConsumer3).getSequence();
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

        assertTrue("Thread was not interrupted", alerted[0]);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsBehind() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final StubEntryConsumer[] entryConsumers = new StubEntryConsumer[3];
        for (int i = 0, size = entryConsumers.length; i < size; i++)
        {
            entryConsumers[i] = new StubEntryConsumer();
            entryConsumers[i].setSequence(expectedNumberMessages - 2);
        }

        final ThresholdBarrier barrier = ringBuffer.createBarrier(entryConsumers);

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (StubEntryConsumer stubWorker : entryConsumers)
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
    public void shouldReturnProvidedRingBuffer()
    {
        assertEquals(ringBuffer, thresholdBarrier.getRingBuffer());
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

    private static class StubEntryConsumer implements EntryConsumer
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
}
