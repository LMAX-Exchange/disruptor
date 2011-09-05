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

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lmax.disruptor.support.DaemonThreadFactory;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.support.TestWaiter;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class RingBufferTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private final RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 32);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    {
        ringBuffer.setGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());
    }

    @Test
    public void shouldClaimAndGet() throws Exception
    {
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, ringBuffer.getCursor());

        StubEvent expectedEvent = new StubEvent(2701);

        long claimSequence = ringBuffer.next();
        StubEvent oldEvent = ringBuffer.get(claimSequence);
        oldEvent.copy(expectedEvent);
        ringBuffer.publish(claimSequence);

        long sequence = sequenceBarrier.waitFor(0);
        assertEquals(0, sequence);

        StubEvent event = ringBuffer.get(sequence);
        assertEquals(expectedEvent , event);

        assertEquals(0L, ringBuffer.getCursor());
    }

    @Test
    public void shouldClaimAndGetWithTimeout() throws Exception
    {
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, ringBuffer.getCursor());

        StubEvent expectedEvent = new StubEvent(2701);

        long claimSequence = ringBuffer.next();
        StubEvent oldEvent = ringBuffer.get(claimSequence);
        oldEvent.copy(expectedEvent);
        ringBuffer.publish(claimSequence);

        long sequence = sequenceBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(0, sequence);

        StubEvent event = ringBuffer.get(sequence);
        assertEquals(expectedEvent, event);

        assertEquals(0L, ringBuffer.getCursor());
    }

    @Test
    public void shouldGetWithTimeout() throws Exception
    {
        long sequence = sequenceBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, sequence);
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        Future<List<StubEvent>> messages = getMessages(0, 0);

        StubEvent expectedEvent = new StubEvent(2701);

        long sequence = ringBuffer.next();
        StubEvent oldEvent = ringBuffer.get(sequence);
        oldEvent.copy(expectedEvent);
        ringBuffer.publish(sequence);

        assertEquals(expectedEvent, messages.get().get(0));
    }

    @Test
    public void shouldClaimAndGetMultipleMessages() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        for (int i = 0; i < numMessages; i++)
        {
            long sequence = ringBuffer.next();
            StubEvent event = ringBuffer.get(sequence);
            event.setValue(i);
            ringBuffer.publish(sequence);
        }

        int expectedSequence = numMessages - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = 0; i < numMessages; i++)
        {
            assertEquals(i, ringBuffer.get(i).getValue());
        }
    }

    @Test
    public void shouldWrap() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        int offset = 1000;
        for (int i = 0; i < numMessages + offset; i++)
        {
            long sequence = ringBuffer.next();
            StubEvent event = ringBuffer.get(sequence);
            event.setValue(i);
            ringBuffer.publish(sequence);
        }

        int expectedSequence = numMessages + offset - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = offset; i < numMessages + offset; i++)
        {
            assertEquals(i, ringBuffer.get(i).getValue());
        }
    }

    @Test
    public void shouldSetAtSpecificSequence() throws Exception
    {
        long expectedSequence = 5;

        ringBuffer.claim(expectedSequence);
        StubEvent expectedEvent = ringBuffer.get(expectedSequence);
        expectedEvent.setValue((int) expectedSequence);
        ringBuffer.forcePublish(expectedSequence);

        long sequence = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, sequence);

        StubEvent event = ringBuffer.get(sequence);
        assertEquals(expectedEvent, event);

        assertEquals(expectedSequence, ringBuffer.getCursor());
    }

    @Test
    public void shouldPreventPublishersOvertakingEventProcessorWrapPoint() throws InterruptedException
    {
        final int ringBufferSize = 4;
        final CountDownLatch latch = new CountDownLatch(ringBufferSize);
        final AtomicBoolean publisherComplete = new AtomicBoolean(false);
        final RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, ringBufferSize);
        final TestEventProcessor processor = new TestEventProcessor(ringBuffer.newBarrier());
        ringBuffer.setGatingSequences(processor.getSequence());

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (int i = 0; i <= ringBufferSize; i++)
                {
                    long sequence = ringBuffer.next();
                    StubEvent event = ringBuffer.get(sequence);
                    event.setValue(i);
                    ringBuffer.publish(sequence);
                    latch.countDown();
                }

                publisherComplete.set(true);
            }
        });
        thread.start();

        latch.await();
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(ringBufferSize - 1)));
        assertFalse(publisherComplete.get());

        processor.run();
        thread.join();

        assertTrue(publisherComplete.get());
    }

    private Future<List<StubEvent>> getMessages(final long initial, final long toWaitFor)
        throws InterruptedException, BrokenBarrierException
    {
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        final Future<List<StubEvent>> f = EXECUTOR.submit(new TestWaiter(cyclicBarrier, sequenceBarrier, ringBuffer, initial, toWaitFor));

        cyclicBarrier.await();

        return f;
    }

    private static final class TestEventProcessor implements EventProcessor
    {
        private final SequenceBarrier sequenceBarrier;
        private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

        public TestEventProcessor(final SequenceBarrier sequenceBarrier)
        {
            this.sequenceBarrier = sequenceBarrier;
        }

        @Override
        public Sequence getSequence()
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
            try
            {
                sequenceBarrier.waitFor(0L);
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }

            sequence.set(sequence.get() + 1L);
        }
    }
}
