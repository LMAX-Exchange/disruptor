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

import com.lmax.disruptor.support.ValueAdditionHandler;
import com.lmax.disruptor.support.ValueAdditionQueueConsumer;
import com.lmax.disruptor.support.ValueEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * UniCast a series of items between 1 producer and 1 consumer.
 *
 * +----+    +----+
 * | P0 |--->| C0 |
 * +----+    +----+
 *
 *
 * Queue Based:
 * ============
 *
 *        put      take
 * +----+    +====+    +----+
 * | P0 |--->| Q0 |<---| C0 |
 * +----+    +====+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * C0 - Consumer 0
 *
 *
 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +-----------------------------+
 *             |                             |
 *             |                             v
 * +----+    +====+    +====+    +====+    +----+
 * | P0 |--->| PB |--->| RB |<---| CB |    | C0 |
 * +----+    +====+    +====+    +====+    +----+
 *                claim      get    ^        |
 *                                  |        |
 *                                  +--------+
 *                                    waitFor
 *
 * P0 - Producer 0
 * PB - ProducerBarrier
 * RB - RingBuffer
 * CB - ConsumerBarrier
 * C0 - Consumer 0
 *
 * </pre>
 */
public final class UniCast1P1CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000L * 1000L * 500L;
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
    private final ValueAdditionQueueConsumer queueConsumer = new ValueAdditionQueueConsumer(blockingQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);
    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final ValueAdditionHandler handler = new ValueAdditionHandler();
    private final BatchConsumer<ValueEntry> batchConsumer = new BatchConsumer<ValueEntry>(consumerBarrier, handler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(batchConsumer);

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
        queueConsumer.reset();
        Future future = EXECUTOR.submit(queueConsumer);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
        }

        final long expectedSequence = ITERATIONS - 1L;
        while (queueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueConsumer.halt();
        future.cancel(true);

        Assert.assertEquals(expectedResult, queueConsumer.getValue());

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass(final int passNumber) throws InterruptedException
    {
        handler.reset();
        EXECUTOR.submit(batchConsumer);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEntry entry = producerBarrier.nextEntry();
            entry.setValue(i);
            producerBarrier.commit(entry);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (batchConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchConsumer.halt();

        Assert.assertEquals(expectedResult, handler.getValue());

        return opsPerSecond;
    }
}
