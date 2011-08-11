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

import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueAdditionQueueProcessor;
import com.lmax.disruptor.support.ValueEvent;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

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
 *              +-------------------+
 *              |                   |
 *              |                   v
 * +----+    +====+    +=====+   +-----+
 * | P1 |--->| RB |<---| EPB |   | EP1 |
 * +----+    +====+    +=====+   +-----+
 *      claim      get    ^         |
 *                        |         |
 *                        +---------+
 *                          waitFor
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * EPB - DependencyBarrier
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class UniCast1P1CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000L * 1000L * 300L;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final long expectedResult;
    {
        long temp = 0L;
        for (long i = 0L; i < ITERATIONS; i++)
        {
            temp += i;
        }

        expectedResult = temp;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final ValueAdditionQueueProcessor queueProcessor = new ValueAdditionQueueProcessor(blockingQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);
    private final DependencyBarrier dependencyBarrier = ringBuffer.newDependencyBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor = new BatchEventProcessor<ValueEvent>(ringBuffer, dependencyBarrier, handler);
    {
        ringBuffer.setTrackedProcessors(batchEventProcessor);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass(final int passNumber) throws InterruptedException
    {
        queueProcessor.reset();
        Future future = EXECUTOR.submit(queueProcessor);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
        }

        final long expectedSequence = ITERATIONS - 1L;
        while (queueProcessor.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueProcessor.halt();
        future.cancel(true);

        Assert.assertEquals(expectedResult, queueProcessor.getValue());

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass(final int passNumber) throws InterruptedException
    {
        handler.reset();
        EXECUTOR.submit(batchEventProcessor);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEvent event = ringBuffer.nextEvent();
            event.setValue(i);
            ringBuffer.publish(event);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (batchEventProcessor.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchEventProcessor.halt();

        Assert.assertEquals(expectedResult, handler.getValue());

        return opsPerSecond;
    }
}
