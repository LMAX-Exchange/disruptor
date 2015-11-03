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

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.dsl.SequencerFactory;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.Util;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public final class SequenceBarrierTest
{
    private final RingBuffer<StubEvent> ringBuffer;
    private StubEventProcessor eventProcessor1 = new StubEventProcessor();
    private StubEventProcessor eventProcessor2 = new StubEventProcessor();
    private StubEventProcessor eventProcessor3 = new StubEventProcessor();

    public SequenceBarrierTest(String name, SequencerFactory sequencerFactory)
    {
        ringBuffer = new RingBuffer<>(
            StubEvent.EVENT_FACTORY, sequencerFactory.newInstance(256, new BlockingWaitStrategy()));

        ringBuffer.addGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters()
    {
        Object[][] params = new Object[][] {
            {"waitfree", ProducerType.waitFree(64)},
            {"multi", ProducerType.MULTI}
        };

        return Arrays.asList(params);
    }

    @Test
    public void shouldWaitForWorkCompleteWhereCompleteWorkThresholdIsAhead() throws Exception
    {
        final long expectedNumberMessages = 10;
        final long expectedWorkSequence = 9;
        fillRingBuffer(expectedNumberMessages);

        eventProcessor1.setSequence(expectedNumberMessages);
        eventProcessor2.setSequence(expectedWorkSequence);
        eventProcessor3.setSequence(expectedNumberMessages);

        SequenceBarrier sequenceBarrier =
            ringBuffer.newBarrier(
                eventProcessor1.getSequence(), eventProcessor2.getSequence(), eventProcessor3.getSequence());

        long completedWorkSequence = sequenceBarrier.waitFor(expectedWorkSequence);
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

        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(Util.getSequencesFor(workers));

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                long sequence = ringBuffer.next();
                StubEvent event = ringBuffer.get(sequence);
                event.setValue((int) sequence);
                ringBuffer.publish(sequence);

                for (StubEventProcessor stubWorker : workers)
                {
                    stubWorker.setSequence(sequence);
                }
            }
        };

        new Thread(runnable).start();

        long completedWorkSequence = sequenceBarrier.waitFor(expectedNumberMessages);
        assertTrue(completedWorkSequence >= expectedNumberMessages);
    }

    @Test
    public void shouldInterruptDuringBusySpin() throws Exception
    {
        final long expectedNumberMessages = 10;
        fillRingBuffer(expectedNumberMessages);

        final CountDownLatch latch = new CountDownLatch(3);
        eventProcessor1.set(new CountDownLatchSequence(8L, latch));
        eventProcessor2.set(new CountDownLatchSequence(8L, latch));
        eventProcessor3.set(new CountDownLatchSequence(8L, latch));

        final SequenceBarrier sequenceBarrier =
            ringBuffer.newBarrier(Util.getSequencesFor(eventProcessor1, eventProcessor2, eventProcessor3));

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

        final StubEventProcessor[] eventProcessors = new StubEventProcessor[3];
        for (int i = 0, size = eventProcessors.length; i < size; i++)
        {
            eventProcessors[i] = new StubEventProcessor();
            eventProcessors[i].setSequence(expectedNumberMessages - 2);
        }

        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier(Util.getSequencesFor(eventProcessors));

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                for (StubEventProcessor stubWorker : eventProcessors)
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

    private static final class StubEventProcessor implements EventProcessor
    {
        private Sequence sequence = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
        private final AtomicBoolean running = new AtomicBoolean(false);

        public void setSequence(long sequence)
        {
            this.sequence.set(sequence);
        }

        @Override
        public Sequence getSequence()
        {
            return sequence;
        }

        public void set(Sequence sequence)
        {
            this.sequence = sequence;
        }

        @Override
        public void halt()
        {
            running.set(false);
        }

        @Override
        public boolean isRunning()
        {
            return running.get();
        }

        @Override
        public void run()
        {
            if (!running.compareAndSet(false, true))
            {
                throw new IllegalStateException("Already running");
            }
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
