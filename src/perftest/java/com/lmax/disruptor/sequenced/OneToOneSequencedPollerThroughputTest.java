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
package com.lmax.disruptor.sequenced;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static com.lmax.disruptor.support.PerfTestUtil.failIfNot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.*;
import com.lmax.disruptor.EventPoller.PollState;
import com.lmax.disruptor.support.PerfTestUtil;
import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.PaddedLong;

/**
 * <pre>
 * UniCast a series of items between 1 publisher and 1 event processor.
 *
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *
 * Disruptor:
 * ==========
 *              track to prevent wrap
 *              +------------------+
 *              |                  |
 *              |                  v
 * +----+    +====+    +====+   +-----+
 * | P1 |--->| RB |<---| SB |   | EP1 |
 * +----+    +====+    +====+   +-----+
 *      claim      get    ^        |
 *                        |        |
 *                        +--------+
 *                          waitFor
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB  - SequenceBarrier
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class OneToOneSequencedPollerThroughputTest extends AbstractPerfTestDisruptor
{
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final long expectedResult = PerfTestUtil.accumulatedAddition(ITERATIONS);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

    private final EventPoller<ValueEvent> poller = ringBuffer.newPoller();
    private final PollRunnable pollRunnable = new PollRunnable(poller);

    {
        ringBuffer.addGatingSequences(poller.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    private static class PollRunnable implements Runnable, EventPoller.Handler<ValueEvent>, BatchStartAware
    {
        private final EventPoller<ValueEvent> poller;
        private volatile boolean running = true;
        private final PaddedLong value = new PaddedLong();
        private final PaddedLong batchesProcessed = new PaddedLong();
        private CountDownLatch latch;
        private long count;

        PollRunnable(EventPoller<ValueEvent> poller)
        {
            this.poller = poller;
        }

        @Override
        public void run()
        {
            try
            {
                while (running)
                {
                    if (PollState.PROCESSING != poller.poll(this))
                    {
                        Thread.yield();
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        @Override
        public boolean onEvent(ValueEvent event, long sequence, boolean endOfBatch)
        {
            value.set(value.get() + event.getValue());

            if (count == sequence)
            {
                latch.countDown();
            }

            return true;
        }

        public void halt()
        {
            running = false;
        }

        public void reset(final CountDownLatch latch, final long expectedCount)
        {
            value.set(0L);
            this.latch = latch;
            count = expectedCount;
            batchesProcessed.set(0);
            running = true;
        }

        public long getValue()
        {
            return value.get();
        }

        public long getBatchesProcessed()
        {
            return batchesProcessed.get();
        }

        @Override
        public void onBatchStart(long batchSize)
        {
            batchesProcessed.increment();
        }
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws InterruptedException
    {
        PerfTestContext perfTestContext = new PerfTestContext();
        final CountDownLatch latch = new CountDownLatch(1);
        long expectedCount = poller.getSequence().get() + ITERATIONS;
        pollRunnable.reset(latch, expectedCount);
        executor.submit(pollRunnable);
        long start = System.currentTimeMillis();

        final RingBuffer<ValueEvent> rb = ringBuffer;

        for (long i = 0; i < ITERATIONS; i++)
        {
            long next = rb.next();
            rb.get(next).setValue(i);
            rb.publish(next);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        perfTestContext.setBatchData(pollRunnable.getBatchesProcessed(), ITERATIONS);
        waitForEventProcessorSequence(expectedCount);
        pollRunnable.halt();

        failIfNot(expectedResult, pollRunnable.getValue());

        return perfTestContext;
    }

    private void waitForEventProcessorSequence(long expectedCount) throws InterruptedException
    {
        while (poller.getSequence().get() != expectedCount)
        {
            Thread.sleep(1);
        }
    }

    public static void main(String[] args) throws Exception
    {
        OneToOneSequencedPollerThroughputTest test = new OneToOneSequencedPollerThroughputTest();
        test.testImplementations();
    }
}
