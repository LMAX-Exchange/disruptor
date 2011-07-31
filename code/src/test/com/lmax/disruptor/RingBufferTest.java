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
import com.lmax.disruptor.support.TestWaiter;
import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class RingBufferTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 20);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    {
        ringBuffer.setTrackedConsumers(new NoOpConsumer(ringBuffer));
    }

    @Test
    public void shouldClaimAndGet() throws Exception
    {
        assertEquals(RingBuffer.INITIAL_CURSOR_VALUE, ringBuffer.getCursor());

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        long sequence = consumerBarrier.waitFor(0);
        assertEquals(0, sequence);

        StubEntry entry = ringBuffer.getEntry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(0L, ringBuffer.getCursor());
    }

    @Test
    public void shouldClaimAndGetWithTimeout() throws Exception
    {
        assertEquals(RingBuffer.INITIAL_CURSOR_VALUE, ringBuffer.getCursor());

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        long sequence = consumerBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(0, sequence);

        StubEntry entry = ringBuffer.getEntry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(0L, ringBuffer.getCursor());
    }


    @Test
    public void shouldGetWithTimeout() throws Exception
    {
        long sequence = consumerBarrier.waitFor(0, 5, TimeUnit.MILLISECONDS);
        assertEquals(RingBuffer.INITIAL_CURSOR_VALUE, sequence);
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        Future<List<StubEntry>> messages = getMessages(0, 0);

        StubEntry expectedEntry = new StubEntry(2701);

        StubEntry oldEntry = ringBuffer.nextEntry();
        oldEntry.copy(expectedEntry);
        ringBuffer.commit(oldEntry);

        assertEquals(expectedEntry, messages.get().get(0));
    }

    @Test
    public void shouldClaimAndGetMultipleMessages() throws Exception
    {
        int numMessages = ringBuffer.getCapacity();
        for (int i = 0; i < numMessages; i++)
        {
            StubEntry entry = ringBuffer.nextEntry();
            entry.setValue(i);
            ringBuffer.commit(entry);
        }

        int expectedSequence = numMessages - 1;
        long available = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = 0; i < numMessages; i++)
        {
            assertEquals(i, ringBuffer.getEntry(i).getValue());
        }
    }

    @Test
    public void shouldWrap() throws Exception
    {
        int numMessages = ringBuffer.getCapacity();
        int offset = 1000;
        for (int i = 0; i < numMessages + offset ; i++)
        {
            StubEntry entry = ringBuffer.nextEntry();
            entry.setValue(i);
            ringBuffer.commit(entry);
        }

        int expectedSequence = numMessages + offset - 1;
        long available = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = offset; i < numMessages + offset; i++)
        {
            assertEquals(i, ringBuffer.getEntry(i).getValue());
        }
    }

    @Test
    public void shouldSetAtSpecificSequence() throws Exception
    {
        long expectedSequence = 5;

        StubEntry expectedEntry = ringBuffer.claimEntryAtSequence(expectedSequence);
        expectedEntry.setValue((int) expectedSequence);
        ringBuffer.commitWithForce(expectedEntry);

        long sequence = consumerBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, sequence);

        StubEntry entry = ringBuffer.getEntry(sequence);
        assertEquals(expectedEntry, entry);

        assertEquals(expectedSequence, ringBuffer.getCursor());
    }

    @Test
    public void shouldPreventProducersOvertakingConsumerWrapPoint() throws InterruptedException
    {
        final int ringBufferSize = 4;
        final CountDownLatch latch = new CountDownLatch(ringBufferSize);
        final AtomicBoolean producerComplete = new AtomicBoolean(false);
        final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, ringBufferSize);
        final TestConsumer consumer = new TestConsumer(ringBuffer.createConsumerBarrier());
        ringBuffer.setTrackedConsumers(consumer);

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (int i = 0; i <= ringBufferSize; i++)
                {
                    StubEntry entry = ringBuffer.nextEntry();
                    entry.setValue(i);
                    ringBuffer.commit(entry);
                    latch.countDown();
                }

                producerComplete.set(true);
            }
        });
        thread.start();

        latch.await();
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(ringBufferSize - 1)));
        assertFalse(producerComplete.get());

        consumer.run();
        thread.join();

        assertTrue(producerComplete.get());
    }

    private Future<List<StubEntry>> getMessages(final long initial, final long toWaitFor)
        throws InterruptedException, BrokenBarrierException
    {
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();

        final Future<List<StubEntry>> f = EXECUTOR.submit(new TestWaiter(cyclicBarrier, consumerBarrier, initial, toWaitFor));

        cyclicBarrier.await();

        return f;
    }

    private static final class TestConsumer implements Consumer
    {
        private final ConsumerBarrier<StubEntry> consumerBarrier;
        private volatile long sequence = RingBuffer.INITIAL_CURSOR_VALUE;

        public TestConsumer(final ConsumerBarrier<StubEntry> consumerBarrier)
        {
            this.consumerBarrier = consumerBarrier;
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
            try
            {
                consumerBarrier.waitFor(0L);
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
            ++sequence;
        }
    }
}
