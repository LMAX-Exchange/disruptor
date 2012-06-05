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

import com.lmax.disruptor.support.PerfTestUtil;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * UniCast a series of items between 1 publisher and 1 event processor.
 * This test illustrates the benefits of writing batches of 10 events
 * for exchange at a time.
 *
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *
 *
 * Queue Based:
 * ============
 *
 *        put      take
 * +----+    +====+    +-----+
 * | P1 |--->| Q1 |<---| EP1 |
 * +----+    +====+    +-----+
 *
 * P1  - Publisher 1
 * Q1  - Queue 1
 * EP1 - EventProcessor 1
 *
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
 * </pre>
 */
public final class OnePublisherToOneProcessorUniCastBatchThroughputTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private final long expectedResult = PerfTestUtil.accumulatedAddition(ITERATIONS);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final PreallocatedRingBuffer<ValueEvent> ringBuffer =
        new PreallocatedRingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, 
                                   new SingleProducerSequencer(BUFFER_SIZE, new YieldingWaitStrategy()));
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handler);
    {
        ringBuffer.setGatingSequences(batchEventProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass() throws InterruptedException
    {
        // Same expected results as UniCast scenario
        return 0L;
    }

    @Override
    protected long runDisruptorPass() throws InterruptedException
    {
        final Sequencer sequencer = ringBuffer.getSequencer();
        final CountDownLatch latch = new CountDownLatch(1);
        handler.reset(latch, batchEventProcessor.getSequence().get() + ITERATIONS);
        EXECUTOR.submit(batchEventProcessor);

        final int batchSize = 10;
        final BatchDescriptor batchDescriptor = sequencer.newBatchDescriptor(batchSize);

        long start = System.currentTimeMillis();

        long offset = 0;
        for (long i = 0; i < ITERATIONS; i += batchSize)
        {
            sequencer.next(batchDescriptor);
            for (long c = batchDescriptor.getStart(), end = batchDescriptor.getEnd(); c <= end; c++)
            {
                ringBuffer.get(c).setValue(offset++);
            }
            sequencer.publish(batchDescriptor);
        }

        latch.await();

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchEventProcessor.halt();

        Assert.assertEquals(expectedResult, handler.getValue());

        return opsPerSecond;
    }
}
