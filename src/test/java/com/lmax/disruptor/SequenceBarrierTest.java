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

import com.lmax.disruptor.support.DummyEventProcessor;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.Util;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public final class SequenceBarrierTest
{
    private final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 64);

    public SequenceBarrierTest()
    {
        ringBuffer.addGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsAhead() throws Exception
    {
        final long expectedNumberMessages = 10;
        final long expectedWorkSequence = 9;
        fillRingBuffer(expectedNumberMessages);

        final Sequence sequence1 = new Sequence(expectedNumberMessages);
        final Sequence sequence2 = new Sequence(expectedWorkSequence);
        final Sequence sequence3 = new Sequence(expectedNumberMessages);

        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(sequence1, sequence2, sequence3);

        long completedWorkSequence = sequenceBarrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereAllWorkersAreBlockedOnRingBuffer() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final DummyEventProcessor[] workers = new DummyEventProcessor[3];
        for (int i = 0, size = workers.length; i < size; i++)
        {
            workers[i] = new DummyEventProcessor();
            workers[i].setSequence(expectedNumberMessages - 1);
        }

        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(Util.getSequencesFor(workers));

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                long sequence = ringBuffer.next();
                StubEvent event = ringBuffer.get(sequence);
                event.setValue((int) sequence);
                ringBuffer.publish(sequence);

                for (DummyEventProcessor stubWorker : workers)
                {
                    stubWorker.setSequence(sequence);
                }
            }
        };

        new Thread(runnable).start();

        long expectedWorkSequence = expectedNumberMessages;
        long completedWorkSequence = sequenceBarrier.waitFor(expectedNumberMessages);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldInterruptDuringBusySpin() throws Exception
    {
        final long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final CountDownLatch latch = new CountDownLatch(3);
        final Sequence sequence1 = new CountDownLatchSequence(8L, latch);
        final Sequence sequence2 = new CountDownLatchSequence(8L, latch);
        final Sequence sequence3 = new CountDownLatchSequence(8L, latch);

        final SequenceBarrier sequenceBarrier =
            ringBuffer.newBarrier(sequence1, sequence2, sequence3);

        final boolean[] alerted = {false};
        Thread t = new Thread(
            new Runnable()
            {
                public void run()
                {
                    try
                    {
                        sequenceBarrier.waitFor(expectedNumberMessages - 1);
                    }
                    catch (AlertException e)
                    {
                        alerted[0] = true;
                    }
                    catch (Exception e)
                    {
                        // don't care
                    }
                }
            });

        t.start();
        latch.await(3, TimeUnit.SECONDS);
        sequenceBarrier.alert();
        t.join();

        assertTrue("Thread was not interrupted", alerted[0]);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsBehind() throws Exception
    {
        long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final DummyEventProcessor[] eventProcessors = new DummyEventProcessor[3];
        for (int i = 0, size = eventProcessors.length; i < size; i++)
        {
            eventProcessors[i] = new DummyEventProcessor();
            eventProcessors[i].setSequence(expectedNumberMessages - 2);
        }

        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(Util.getSequencesFor(eventProcessors));

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (DummyEventProcessor stubWorker : eventProcessors)
                {
                    stubWorker.setSequence(stubWorker.getSequence().get() + 1L);
                }
            }
        };

        Thread thread = new Thread(runnable);
        thread.start();
        thread.join();

        long expectedWorkSequence = expectedNumberMessages - 1;
        long completedWorkSequence = sequenceBarrier.waitFor(expectedWorkSequence);
        assertTrue(completedWorkSequence >= expectedWorkSequence);
    }

    @Test
    public void shouldSetAndClearAlertStatus()
    {
        SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        assertFalse(sequenceBarrier.isAlerted());

        sequenceBarrier.alert();
        assertTrue(sequenceBarrier.isAlerted());

        sequenceBarrier.clearAlert();
        assertFalse(sequenceBarrier.isAlerted());
    }

    private void fillRingBuffer(long expectedNumberMessages) throws InterruptedException
    {
        for (long i = 0; i < expectedNumberMessages; i++)
        {
            long sequence = ringBuffer.next();
            StubEvent event = ringBuffer.get(sequence);
            event.setValue((int) i);
            ringBuffer.publish(sequence);
        }
    }

    private static final class CountDownLatchSequence extends Sequence
    {
        private final CountDownLatch latch;

        private CountDownLatchSequence(final long initialValue, final CountDownLatch latch)
        {
            super(initialValue);
            this.latch = latch;
        }

        @Override
        public long get()
        {
            latch.countDown();
            return super.get();
        }
    }
}
