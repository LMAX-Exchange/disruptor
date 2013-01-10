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

import static com.lmax.disruptor.dsl.ProducerType.SINGLE;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.Assert;
import org.junit.Test;

import com.lmax.disruptor.support.PerfTestUtil;
import com.lmax.disruptor.support.ValueAdditionEntryHandler;
import com.lmax.disruptor.support.ValueAdditionQueueProcessor;
import com.lmax.disruptor.support.ValueEntry;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * <pre>
 * UniCast a series of items between 1 publisher and 1 event processor.
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
 *
 * </pre>
 */
public final class OnePublisherToOneProcessorUniCastOffHeapThroughputTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 1000L;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final long expectedResult = PerfTestUtil.accumulatedAddition(ITERATIONS);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final ValueAdditionQueueProcessor queueProcessor = 
            new ValueAdditionQueueProcessor(blockingQueue, ITERATIONS - 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final OffHeapRingBuffer<ValueEntry> ringBuffer =
        OffHeapRingBuffer.newInstance(SINGLE, new YieldingWaitStrategy(), ValueEntry.FACTORY, BUFFER_SIZE, 16);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEntryHandler handler = new ValueAdditionEntryHandler();
    private final BatchEventProcessor<ValueEntry> batchEventProcessor = 
            new BatchEventProcessor<ValueEntry>(ringBuffer.createDataSource(), sequenceBarrier, handler);
    {
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());
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
        final CountDownLatch latch = new CountDownLatch(1);
        queueProcessor.reset(latch);
        Future<?> future = EXECUTOR.submit(queueProcessor);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
        }

        latch.await();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueProcessor.halt();
        future.cancel(true);

        Assert.assertEquals(expectedResult, queueProcessor.getValue());

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        long expectedCount = batchEventProcessor.getSequence().get() + ITERATIONS;
        handler.reset(latch, expectedCount);
        EXECUTOR.submit(batchEventProcessor);
        long start = System.currentTimeMillis();
        
        final Producer<ValueEntry> p = ringBuffer.createProducer();

        for (long i = 0; i < ITERATIONS; i++)
        {
            p.next().setValue(i);
            p.publish();
        }

        latch.await();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        waitForEventProcessorSequence(expectedCount);
        batchEventProcessor.halt();

        Assert.assertEquals(expectedResult, handler.getValue());
        
        return opsPerSecond;
    }

    private void waitForEventProcessorSequence(long expectedCount) throws InterruptedException
    {
        while (batchEventProcessor.getSequence().get() != expectedCount)
        {
            Thread.sleep(1);
        }
    }
    
    public static void main(String[] args) throws Exception
    {
        OnePublisherToOneProcessorUniCastOffHeapThroughputTest test = 
                new OnePublisherToOneProcessorUniCastOffHeapThroughputTest();
        test.shouldCompareDisruptorVsQueues();
    }
}
