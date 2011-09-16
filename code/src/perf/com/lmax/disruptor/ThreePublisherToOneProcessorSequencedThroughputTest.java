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

import com.lmax.disruptor.support.*;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 *
 * Sequence a series of events from multiple publishers going to one event processor.
 *
 * +----+
 * | P1 |------+
 * +----+      |
 *             v
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *             ^
 * +----+      |
 * | P3 |------+
 * +----+
 *
 *
 * Queue Based:
 * ============
 *
 * +----+  put
 * | P1 |------+
 * +----+      |
 *             v   take
 * +----+    +====+    +-----+
 * | P2 |--->| Q1 |<---| EP1 |
 * +----+    +====+    +-----+
 *             ^
 * +----+      |
 * | P3 |------+
 * +----+
 *
 * P1  - Publisher 1
 * P2  - Publisher 2
 * P3  - Publisher 3
 * Q1  - Queue 1
 * EP1 - EventProcessor 1
 *
 *
 * Disruptor:
 * ==========
 *             track to prevent wrap
 *             +--------------------+
 *             |                    |
 *             |                    v
 * +----+    +====+    +====+    +-----+
 * | P1 |--->| RB |<---| SB |    | EP1 |
 * +----+    +====+    +====+    +-----+
 *             ^   get    ^         |
 * +----+      |          |         |
 * | P2 |------+          +---------+
 * +----+      |            waitFor
 *             |
 * +----+      |
 * | P3 |------+
 * +----+
 *
 * P1  - Publisher 1
 * P2  - Publisher 2
 * P3  - Publisher 3
 * RB  - RingBuffer
 * SB  - SequenceBarrier
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class ThreePublisherToOneProcessorSequencedThroughputTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_PUBLISHERS = 3;
    private static final int SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 300L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_PUBLISHERS + 1);
    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PUBLISHERS + 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final ValueAdditionQueueProcessor queueProcessor = new ValueAdditionQueueProcessor(blockingQueue);
    private final ValueQueuePublisher[] valueQueuePublishers = new ValueQueuePublisher[NUM_PUBLISHERS];
    {
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            valueQueuePublishers[i] = new ValueQueuePublisher(cyclicBarrier, blockingQueue, ITERATIONS);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, SIZE,
                                   ClaimStrategy.Option.MULTI_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handler);
    private final ValuePublisher[] valuePublishers = new ValuePublisher[NUM_PUBLISHERS];
    {
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            valuePublishers[i] = new ValuePublisher(cyclicBarrier, ringBuffer, ITERATIONS);
        }

        ringBuffer.setGatingSequences(batchEventProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass(final int passNumber) throws Exception
    {
        Future[] futures = new Future[NUM_PUBLISHERS];
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i] = EXECUTOR.submit(valueQueuePublishers[i]);
        }
        Future processorFuture = EXECUTOR.submit(queueProcessor);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i].get();
        }

        final long expectedSequence = (ITERATIONS * NUM_PUBLISHERS) - 1L;
        while (expectedSequence > queueProcessor.getSequence())
        {
            // busy spin
        }

        long opsPerSecond = (NUM_PUBLISHERS * ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchEventProcessor.halt();
        processorFuture.cancel(true);

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass(final int passNumber) throws Exception
    {
        Future[] futures = new Future[NUM_PUBLISHERS];
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i] = EXECUTOR.submit(valuePublishers[i]);
        }
        EXECUTOR.submit(batchEventProcessor);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i].get();
        }

        final long expectedSequence = (ITERATIONS * NUM_PUBLISHERS * (passNumber + 1L)) - 1L;
        while (expectedSequence > batchEventProcessor.getSequence().get())
        {
            // busy spin
        }

        long opsPerSecond = (NUM_PUBLISHERS * ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchEventProcessor.halt();

        return opsPerSecond;
    }
}
