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

import com.lmax.disruptor.support.FizzBuzzEvent;
import com.lmax.disruptor.support.FizzBuzzEventHandler;
import com.lmax.disruptor.support.FizzBuzzQueueProcessor;
import com.lmax.disruptor.support.FizzBuzzStep;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * Produce an event replicated to two event processors and fold back to a single third event processor.
 *
 *           +-----+
 *    +----->| EP1 |------+
 *    |      +-----+      |
 *    |                   v
 * +----+              +-----+
 * | P1 |              | EP3 |
 * +----+              +-----+
 *    |                   ^
 *    |      +-----+      |
 *    +----->| EP2 |------+
 *           +-----+
 *
 *
 * Queue Based:
 * ============
 *                 take       put
 *     put   +====+    +-----+    +====+  take
 *    +----->| Q1 |<---| EP1 |--->| Q3 |<------+
 *    |      +====+    +-----+    +====+       |
 *    |                                        |
 * +----+    +====+    +-----+    +====+    +-----+
 * | P1 |--->| Q2 |<---| EP2 |--->| Q4 |<---| EP3 |
 * +----+    +====+    +-----+    +====+    +-----+
 *
 * P1  - Publisher 1
 * Q1  - Queue 1
 * Q2  - Queue 2
 * Q3  - Queue 3
 * Q4  - Queue 4
 * EP1 - EventProcessor 1
 * EP2 - EventProcessor 2
 * EP3 - EventProcessor 3
 *
 *
 * Disruptor:
 * ==========
 *                    track to prevent wrap
 *              +-------------------------------+
 *              |                               |
 *              |                               v
 * +----+    +====+               +=====+    +-----+
 * | P1 |--->| RB |<--------------| SB2 |<---| EP3 |
 * +----+    +====+               +=====+    +-----+
 *      claim   ^  get               |   waitFor
 *              |                    |
 *           +=====+    +-----+      |
 *           | SB1 |<---| EP1 |<-----+
 *           +=====+    +-----+      |
 *              ^                    |
 *              |       +-----+      |
 *              +-------| EP2 |<-----+
 *             waitFor  +-----+
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB1 - SequenceBarrier 1
 * EP1 - EventProcessor 1
 * EP2 - EventProcessor 2
 * SB2 - SequenceBarrier 2
 * EP3 - EventProcessor 3
 *
 * </pre>
 */
public final class OnePublisherToThreeProcessorDiamondThroughputTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

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

    private final BlockingQueue<Long> fizzInputQueue = new ArrayBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> buzzInputQueue = new ArrayBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Boolean> fizzOutputQueue = new ArrayBlockingQueue<Boolean>(BUFFER_SIZE);
    private final BlockingQueue<Boolean> buzzOutputQueue = new ArrayBlockingQueue<Boolean>(BUFFER_SIZE);

    private final FizzBuzzQueueProcessor fizzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    private final FizzBuzzQueueProcessor buzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    private final FizzBuzzQueueProcessor fizzBuzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ_BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FizzBuzzEvent> ringBuffer =
        new RingBuffer<FizzBuzzEvent>(FizzBuzzEvent.EVENT_FACTORY, BUFFER_SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

    private final FizzBuzzEventHandler fizzHandler = new FizzBuzzEventHandler(FizzBuzzStep.FIZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorFizz =
        new BatchEventProcessor<FizzBuzzEvent>(ringBuffer, sequenceBarrier, fizzHandler);

    private final FizzBuzzEventHandler buzzHandler = new FizzBuzzEventHandler(FizzBuzzStep.BUZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorBuzz =
        new BatchEventProcessor<FizzBuzzEvent>(ringBuffer, sequenceBarrier, buzzHandler);

    private final SequenceBarrier sequenceBarrierFizzBuzz =
        ringBuffer.newBarrier(batchProcessorFizz.getSequence(), batchProcessorBuzz.getSequence());

    private final FizzBuzzEventHandler fizzBuzzHandler = new FizzBuzzEventHandler(FizzBuzzStep.FIZZ_BUZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorFizzBuzz =
            new BatchEventProcessor<FizzBuzzEvent>(ringBuffer, sequenceBarrierFizzBuzz, fizzBuzzHandler);
    {
        ringBuffer.setGatingSequences(batchProcessorFizzBuzz.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        fizzBuzzQueueProcessor.reset(latch);

        Future[] futures = new Future[NUM_EVENT_PROCESSORS];
        futures[0] = EXECUTOR.submit(fizzQueueProcessor);
        futures[1] = EXECUTOR.submit(buzzQueueProcessor);
        futures[2] = EXECUTOR.submit(fizzBuzzQueueProcessor);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            Long value = Long.valueOf(i);
            fizzInputQueue.put(value);
            buzzInputQueue.put(value);
        }

        latch.await();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        fizzQueueProcessor.halt();
        buzzQueueProcessor.halt();
        fizzBuzzQueueProcessor.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }

        Assert.assertEquals(expectedResult, fizzBuzzQueueProcessor.getFizzBuzzCounter());

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        fizzBuzzHandler.reset(latch, batchProcessorFizzBuzz.getSequence().get() + ITERATIONS);

        EXECUTOR.submit(batchProcessorFizz);
        EXECUTOR.submit(batchProcessorBuzz);
        EXECUTOR.submit(batchProcessorFizzBuzz);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setValue(i);
            ringBuffer.publish(sequence);
        }

        latch.await();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        batchProcessorFizz.halt();
        batchProcessorBuzz.halt();
        batchProcessorFizzBuzz.halt();

        Assert.assertEquals(expectedResult, fizzBuzzHandler.getFizzBuzzCounter());

        return opsPerSecond;
    }
}
