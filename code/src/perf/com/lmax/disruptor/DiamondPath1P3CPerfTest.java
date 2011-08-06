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
 *              +--------------------------------+
 *              |                                |
 *              |                                v
 * +----+    +====+               +======+    +-----+
 * | P1 |--->| RB |<--------------| EPB2 |<---| EP3 |
 * +----+    +====+               +======+    +-----+
 *      claim   ^  get                |   waitFor
 *              |                     |
 *           +======+    +-----+      |
 *           | EPB1 |<---| EP1 |<-----+
 *           +======+    +-----+      |
 *              ^                     |
 *              |        +-----+      |
 *              +--------| EP2 |<-----+
 *             waitFor   +-----+
 *
 * P1   - Publisher 1
 * RB   - RingBuffer
 * EPB1 - DependencyBarrier 1
 * EP1  - EventProcessor 1
 * EP2  - EventProcessor 2
 * EPB2 - DependencyBarrier 2
 * EP3  - EventProcessor 3
 *
 * </pre>
 */
public final class DiamondPath1P3CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000 * 1000 * 300;
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

    private final BlockingQueue<Long> fizzInputQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> buzzInputQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Boolean> fizzOutputQueue = new ArrayBlockingQueue<Boolean>(SIZE);
    private final BlockingQueue<Boolean> buzzOutputQueue = new ArrayBlockingQueue<Boolean>(SIZE);

    private final FizzBuzzQueueProcessor fizzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    private final FizzBuzzQueueProcessor buzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    private final FizzBuzzQueueProcessor fizzBuzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ_BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FizzBuzzEvent> ringBuffer =
        new RingBuffer<FizzBuzzEvent>(FizzBuzzEvent.EVENT_FACTORY, SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final DependencyBarrier<FizzBuzzEvent> dependencyBarrier = ringBuffer.createDependencyBarrier();

    private final FizzBuzzEventHandler fizzHandler = new FizzBuzzEventHandler(FizzBuzzStep.FIZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorFizz =
        new BatchEventProcessor<FizzBuzzEvent>(dependencyBarrier, fizzHandler);

    private final FizzBuzzEventHandler buzzHandler = new FizzBuzzEventHandler(FizzBuzzStep.BUZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorBuzz =
        new BatchEventProcessor<FizzBuzzEvent>(dependencyBarrier, buzzHandler);

    private final DependencyBarrier<FizzBuzzEvent> dependencyBarrierFizzBuzz =
        ringBuffer.createDependencyBarrier(batchProcessorFizz, batchProcessorBuzz);

    private final FizzBuzzEventHandler fizzBuzzHandler = new FizzBuzzEventHandler(FizzBuzzStep.FIZZ_BUZZ);
    private final BatchEventProcessor<FizzBuzzEvent> batchProcessorFizzBuzz =
            new BatchEventProcessor<FizzBuzzEvent>(dependencyBarrierFizzBuzz, fizzBuzzHandler);
    {
        ringBuffer.setTrackedProcessors(batchProcessorFizzBuzz);
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
    protected long runDisruptorPass(int PassNumber) throws Exception
    {
        fizzBuzzHandler.reset();

        EXECUTOR.submit(batchProcessorFizz);
        EXECUTOR.submit(batchProcessorBuzz);
        EXECUTOR.submit(batchProcessorFizzBuzz);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            FizzBuzzEvent event = ringBuffer.nextEvent();
            event.setValue(i);
            ringBuffer.publish(event);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (batchProcessorFizzBuzz.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        batchProcessorFizz.halt();
        batchProcessorBuzz.halt();
        batchProcessorFizzBuzz.halt();

        Assert.assertEquals(expectedResult, fizzBuzzHandler.getFizzBuzzCounter());

        return opsPerSecond;
    }

    @Override
    protected long runQueuePass(int passNumber) throws Exception
    {
        fizzBuzzQueueProcessor.reset();

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

        final long expectedSequence = ITERATIONS - 1;
        while (fizzBuzzQueueProcessor.getSequence() < expectedSequence)
        {
            // busy spin
        }

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
}
