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
package com.lmax.disruptor.queue;

import static com.lmax.disruptor.support.PerfTestUtil.failIf;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import com.lmax.disruptor.AbstractPerfTestQueue;
import com.lmax.disruptor.support.FizzBuzzQueueProcessor;
import com.lmax.disruptor.support.FizzBuzzStep;
import com.lmax.disruptor.util.DaemonThreadFactory;

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
public final class OneToThreeDiamondQueueThroughputTest extends AbstractPerfTestQueue
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS, DaemonThreadFactory.INSTANCE);

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

    private final BlockingQueue<Long> fizzInputQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> buzzInputQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Boolean> fizzOutputQueue = new LinkedBlockingQueue<Boolean>(BUFFER_SIZE);
    private final BlockingQueue<Boolean> buzzOutputQueue = new LinkedBlockingQueue<Boolean>(BUFFER_SIZE);

    private final FizzBuzzQueueProcessor fizzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    private final FizzBuzzQueueProcessor buzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    private final FizzBuzzQueueProcessor fizzBuzzQueueProcessor =
        new FizzBuzzQueueProcessor(FizzBuzzStep.FIZZ_BUZZ, fizzInputQueue, buzzInputQueue, fizzOutputQueue, buzzOutputQueue, ITERATIONS - 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 4;
    }

    @Override
    protected long runQueuePass() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        fizzBuzzQueueProcessor.reset(latch);

        Future<?>[] futures = new Future[NUM_EVENT_PROCESSORS];
        futures[0] = executor.submit(fizzQueueProcessor);
        futures[1] = executor.submit(buzzQueueProcessor);
        futures[2] = executor.submit(fizzBuzzQueueProcessor);

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

        for (Future<?> future : futures)
        {
            future.cancel(true);
        }

        failIf(expectedResult, 0);

        return opsPerSecond;
    }

    public static void main(String[] args) throws Exception
    {
        new OneToThreeDiamondQueueThroughputTest().testImplementations();
    }
}
