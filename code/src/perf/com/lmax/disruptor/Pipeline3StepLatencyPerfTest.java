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

import com.lmax.disruptor.collections.Histogram;
import com.lmax.disruptor.support.*;
import org.junit.Test;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.concurrent.*;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * <pre>
 *
 * Pipeline a series of stages from a producer to ultimate consumer.
 * Each consumer depends on the output of the previous consumer.
 *
 * +----+    +----+    +----+    +----+
 * | P0 |--->| C0 |--->| C1 |--->| C2 |
 * +----+    +----+    +----+    +----+
 *
 *
 * Queue Based:
 * ============
 *
 *        put      take       put      take       put      take
 * +----+    +====+    +----+    +====+    +----+    +====+    +----+
 * | P0 |--->| Q0 |<---| C0 |--->| Q1 |<---| C1 |--->| Q2 |<---| C2 |
 * +----+    +====+    +----+    +====+    +----+    +====+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * C0 - Consumer 0
 * Q1 - Queue 1
 * C1 - Consumer 1
 * Q2 - Queue 2
 * C2 - Consumer 1
 *
 *
 * Disruptor:
 * ==========
 *                           track to prevent wrap
 *              +-------------------------------------------------------------+
 *              |                                                             |
 *              |                                                             v
 * +----+    +====+    +=====+    +----+    +=====+    +----+    +=====+    +----+
 * | P0 |--->| RB |    | CB0 |<---| C0 |<---| CB1 |<---| C1 |<---| CB2 |<---| C2 |
 * +----+    +====+    +=====+    +----+    +=====+    +----+    +=====+    +----+
 *      claim   ^  get    |  waitFor           |  waitFor           |  waitFor
 *              |         |                    |                    |
 *              +---------+--------------------+--------------------+
 *
 *
 * P0  - Producer 0
 * RB  - RingBuffer
 * CB0 - ConsumerBarrier 0
 * C0  - Consumer 0
 * CB1 - ConsumerBarrier 1
 * C1  - Consumer 1
 * CB2 - ConsumerBarrier 2
 * C2  - Consumer 2
 *
 * </pre>
 *
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class Pipeline3StepLatencyPerfTest
{
    private static final int NUM_CONSUMERS = 3;
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000 * 1000 * 50;
    private static final long PAUSE_NANOS = 1000;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);

    private final Histogram histogram;
    {
        long[] intervals = new long[31];
        long intervalUpperBound = 1L;
        for (int i = 0, size = intervals.length - 1; i < size; i++)
        {
            intervalUpperBound *= 2;
            intervals[i] = intervalUpperBound;
        }

        intervals[intervals.length - 1] = Long.MAX_VALUE;
        histogram = new Histogram(intervals);
    }

    private final long nanoTimeCost;
    {
        final long iterations = 10000000;
        long start = System.nanoTime();
        long finish = start;

        for (int i = 0; i < iterations; i++)
        {
            finish = System.nanoTime();
        }

        if (finish <= start)
        {
            throw new IllegalStateException();
        }

        finish = System.nanoTime();
        nanoTimeCost = (finish - start) / iterations;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> stepOneQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> stepTwoQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> stepThreeQueue = new ArrayBlockingQueue<Long>(SIZE);

    private final LatencyStepQueueConsumer stepOneQueueConsumer =
        new LatencyStepQueueConsumer(FunctionStep.ONE, stepOneQueue, stepTwoQueue, histogram, nanoTimeCost);
    private final LatencyStepQueueConsumer stepTwoQueueConsumer =
        new LatencyStepQueueConsumer(FunctionStep.TWO, stepTwoQueue, stepThreeQueue, histogram, nanoTimeCost);
    private final LatencyStepQueueConsumer stepThreeQueueConsumer =
        new LatencyStepQueueConsumer(FunctionStep.THREE, stepThreeQueue, null, histogram, nanoTimeCost);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.BUSY_SPIN);

    private final ConsumerBarrier<ValueEntry> stepOneConsumerBarrier = ringBuffer.createConsumerBarrier();
    private final LatencyStepHandler stepOneFunctionHandler = new LatencyStepHandler(FunctionStep.ONE, histogram, nanoTimeCost);
    private final BatchConsumer<ValueEntry> stepOneBatchConsumer =
        new BatchConsumer<ValueEntry>(stepOneConsumerBarrier, stepOneFunctionHandler);

    private final ConsumerBarrier<ValueEntry> stepTwoConsumerBarrier = ringBuffer.createConsumerBarrier(stepOneBatchConsumer);
    private final LatencyStepHandler stepTwoFunctionHandler = new LatencyStepHandler(FunctionStep.TWO, histogram, nanoTimeCost);
    private final BatchConsumer<ValueEntry> stepTwoBatchConsumer =
        new BatchConsumer<ValueEntry>(stepTwoConsumerBarrier, stepTwoFunctionHandler);

    private final ConsumerBarrier<ValueEntry> stepThreeConsumerBarrier = ringBuffer.createConsumerBarrier(stepTwoBatchConsumer);
    private final LatencyStepHandler stepThreeFunctionHandler = new LatencyStepHandler(FunctionStep.THREE, histogram, nanoTimeCost);
    private final BatchConsumer<ValueEntry> stepThreeBatchConsumer =
        new BatchConsumer<ValueEntry>(stepThreeConsumerBarrier, stepThreeFunctionHandler);
    {
        ringBuffer.setTrackedConsumers(stepThreeBatchConsumer);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        final int RUNS = 3;

        for (int i = 0; i < RUNS; i++)
        {
            System.gc();

            histogram.clear();
            runDisruptorPass();
            assertThat(Long.valueOf(histogram.getCount()), is(Long.valueOf(ITERATIONS)));
            final BigDecimal disruptorMeanLatency = histogram.getMean();
            System.out.format("%s run %d Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(System.out);

            histogram.clear();
            runQueuePass();
            assertThat(Long.valueOf(histogram.getCount()), is(Long.valueOf(ITERATIONS)));
            final BigDecimal queueMeanLatency = histogram.getMean();
            System.out.format("%s run %d Queues %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(System.out);

            assertTrue(queueMeanLatency.compareTo(disruptorMeanLatency) > 0);
        }
    }

    private void dumpHistogram(final PrintStream out)
    {
        for (int i = 0, size = histogram.getSize(); i < size; i++)
        {
            out.print(histogram.getUpperBoundAt(i));
            out.print('\t');
            out.print(histogram.getCountAt(i));
            out.println();
        }
    }

    private void runDisruptorPass()
    {
        EXECUTOR.submit(stepOneBatchConsumer);
        EXECUTOR.submit(stepTwoBatchConsumer);
        EXECUTOR.submit(stepThreeBatchConsumer);

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEntry entry = ringBuffer.nextEntry();
            entry.setValue(System.nanoTime());
            ringBuffer.commit(entry);

            long pauseStart = System.nanoTime();
            while (PAUSE_NANOS > (System.nanoTime() -  pauseStart))
            {
                // busy spin
            }
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (stepThreeBatchConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        stepOneBatchConsumer.halt();
        stepTwoBatchConsumer.halt();
        stepThreeBatchConsumer.halt();
    }

    private void runQueuePass() throws Exception
    {
        stepThreeQueueConsumer.reset();

        Future[] futures = new Future[NUM_CONSUMERS];
        futures[0] = EXECUTOR.submit(stepOneQueueConsumer);
        futures[1] = EXECUTOR.submit(stepTwoQueueConsumer);
        futures[2] = EXECUTOR.submit(stepThreeQueueConsumer);

        for (long i = 0; i < ITERATIONS; i++)
        {
            stepOneQueue.put(Long.valueOf(System.nanoTime()));

            long pauseStart = System.nanoTime();
            while (PAUSE_NANOS > (System.nanoTime() -  pauseStart))
            {
                // busy spin
            }
        }

        final long expectedSequence = ITERATIONS - 1;
        while (stepThreeQueueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        stepOneQueueConsumer.halt();
        stepTwoQueueConsumer.halt();
        stepThreeQueueConsumer.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }
    }
}
