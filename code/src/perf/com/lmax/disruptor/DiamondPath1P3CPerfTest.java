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

import com.lmax.disruptor.support.FizzBuzzEntry;
import com.lmax.disruptor.support.FizzBuzzHandler;
import com.lmax.disruptor.support.FizzBuzzQueueConsumer;
import com.lmax.disruptor.support.FizzBuzzStep;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * Produce an event replicated to two consumers and fold back to a single third consumer.
 *
 *           +----+
 *    +----->| C0 |-----+
 *    |      +----+     |
 *    |                 v
 * +----+             +----+
 * | P0 |             | C2 |
 * +----+             +----+
 *    |                 ^
 *    |      +----+     |
 *    +----->| C1 |-----+
 *           +----+
 *
 *
 * Queue Based:
 * ============
 *                 take       put
 *     put   +====+    +----+    +====+  take
 *    +----->| Q0 |<---| C0 |--->| Q2 |<-----+
 *    |      +====+    +----+    +====+      |
 *    |                                      |
 * +----+    +====+    +----+    +====+    +----+
 * | P0 |--->| Q1 |<---| C1 |--->| Q3 |<---| C2 |
 * +----+    +====+    +----+    +====+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * Q1 - Queue 1
 * Q2 - Queue 2
 * Q3 - Queue 3
 * C0 - Consumer 0
 * C1 - Consumer 1
 * C2 - Consumer 2
 *
 *
 * Disruptor:
 * ==========
 *                      track to prevent wrap
 *             +--------------------------------------+
 *             |                                      |
 *             |                                      v
 * +----+    +====+    +====+            +=====+    +----+
 * | P0 |--->| PB |--->| RB |<-----------| CB1 |<---| C2 |
 * +----+    +====+    +====+            +=====+    +----+
 *                claim   ^  get            |   waitFor
 *                        |                 |
 *                     +=====+    +----+    |
 *                     | CB0 |<---| C0 |<---+
 *                     +=====+    +----+    |
 *                        ^                 |
 *                        |       +----+    |
 *                        +-------| C1 |<---+
 *                      waitFor   +----+
 *
 * P0  - Producer 0
 * PB  - ProducerBarrier
 * RB  - RingBuffer
 * CB0 - ConsumerBarrier 0
 * C0  - Consumer 0
 * C1  - Consumer 1
 * CB1 - ConsumerBarrier 1
 * C2  - Consumer 2
 *
 * </pre>
 */
public final class DiamondPath1P3CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_CONSUMERS = 3;
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000 * 1000 * 500;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);

    private final long expectedResult;
    {
        long temp = 0L;

        for (long i = 0; i < ITERATIONS; i++)
        {
            boolean fizz = 0 == (i % 3L);
            boolean buzz = 0 == (i % 5L);

            if (fizz && buzz)
            {
                ++temp;
            }
        }

        expectedResult = temp;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> fizzInputQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> buzzInputQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Boolean> fizzOutputQueue = new ArrayBlockingQueue<Boolean>(SIZE);
    private final BlockingQueue<Boolean> buzzOutputQueue = new ArrayBlockingQueue<Boolean>(SIZE);

    private final FizzBuzzQueueConsumer fizzQueueConsumer =
        new FizzBuzzQueueConsumer(FizzBuzzStep.FIZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    private final FizzBuzzQueueConsumer buzzQueueConsumer =
        new FizzBuzzQueueConsumer(FizzBuzzStep.BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    private final FizzBuzzQueueConsumer fizzBuzzQueueConsumer =
        new FizzBuzzQueueConsumer(FizzBuzzStep.FIZZ_BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FizzBuzzEntry> ringBuffer =
        new RingBuffer<FizzBuzzEntry>(FizzBuzzEntry.ENTRY_FACTORY, SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<FizzBuzzEntry> consumerBarrier = ringBuffer.createConsumerBarrier();

    private final FizzBuzzHandler fizzHandler = new FizzBuzzHandler(FizzBuzzStep.FIZZ);
    private final BatchConsumer<FizzBuzzEntry> batchConsumerFizz =
        new BatchConsumer<FizzBuzzEntry>(consumerBarrier, fizzHandler);

    private final FizzBuzzHandler buzzHandler = new FizzBuzzHandler(FizzBuzzStep.BUZZ);
    private final BatchConsumer<FizzBuzzEntry> batchConsumerBuzz =
        new BatchConsumer<FizzBuzzEntry>(consumerBarrier, buzzHandler);

    private final ConsumerBarrier<FizzBuzzEntry> consumerBarrierFizzBuzz =
        ringBuffer.createConsumerBarrier(batchConsumerFizz, batchConsumerBuzz);

    private final FizzBuzzHandler fizzBuzzHandler = new FizzBuzzHandler(FizzBuzzStep.FIZZ_BUZZ);
    private final BatchConsumer<FizzBuzzEntry> batchConsumerFizzBuzz =
            new BatchConsumer<FizzBuzzEntry>(consumerBarrierFizzBuzz, fizzBuzzHandler);

    private final ProducerBarrier<FizzBuzzEntry> producerBarrier = ringBuffer.createProducerBarrier(batchConsumerFizzBuzz);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runDisruptorPass(int PassNumber) throws Exception
    {
        fizzBuzzHandler.reset();

        EXECUTOR.submit(batchConsumerFizz);
        EXECUTOR.submit(batchConsumerBuzz);
        EXECUTOR.submit(batchConsumerFizzBuzz);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            FizzBuzzEntry entry = producerBarrier.nextEntry();
            entry.setValue(i);
            producerBarrier.commit(entry);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (batchConsumerFizzBuzz.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        batchConsumerFizz.halt();
        batchConsumerBuzz.halt();
        batchConsumerFizzBuzz.halt();

        Assert.assertEquals(expectedResult, fizzBuzzHandler.getFizzBuzzCounter());

        return opsPerSecond;
    }

    @Override
    protected long runQueuePass(int passNumber) throws Exception
    {
        fizzBuzzQueueConsumer.reset();

        Future[] futures = new Future[NUM_CONSUMERS];
        futures[0] = EXECUTOR.submit(fizzQueueConsumer);
        futures[1] = EXECUTOR.submit(buzzQueueConsumer);
        futures[2] = EXECUTOR.submit(fizzBuzzQueueConsumer);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            Long value = Long.valueOf(i);
            fizzInputQueue.put(value);
            buzzInputQueue.put(value);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (fizzBuzzQueueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        fizzQueueConsumer.halt();
        buzzQueueConsumer.halt();
        fizzBuzzQueueConsumer.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }

        Assert.assertEquals(expectedResult, fizzBuzzQueueConsumer.getFizzBuzzCounter());

        return opsPerSecond;
    }
}
