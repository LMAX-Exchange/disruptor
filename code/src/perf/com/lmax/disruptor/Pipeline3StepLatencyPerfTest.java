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
 * Pipeline a series of stages from a publisher to ultimate event processor.
 * Each event processor depends on the output of the event processor.
 *
 * +----+    +-----+    +-----+    +-----+
 * | P1 |--->| EP1 |--->| EP2 |--->| EP3 |
 * +----+    +-----+    +-----+    +-----+
 *
 *
 * Queue Based:
 * ============
 *
 *        put      take        put      take        put      take
 * +----+    +====+    +-----+    +====+    +-----+    +====+    +-----+
 * | P1 |--->| Q1 |<---| EP1 |--->| Q2 |<---| EP2 |--->| Q3 |<---| EP3 |
 * +----+    +====+    +-----+    +====+    +-----+    +====+    +-----+
 *
 * P1  - Publisher 1
 * Q1  - Queue 1
 * EP1 - EventProcessor 1
 * Q2  - Queue 2
 * EP2 - EventProcessor 2
 * Q3  - Queue 3
 * EP3 - EventProcessor 3
 *
 *
 * Disruptor:
 * ==========
 *                           track to prevent wrap
 *              +-------------------------------------------------------------------+
 *              |                                                                   |
 *              |                                                                   v
 * +----+    +====+    +======+    +-----+    +======+    +-----+    +======+    +-----+
 * | P1 |--->| RB |    | EPB1 |<---| EP1 |<---| EPB2 |<---| EP2 |<---| EPB3 |<---| EP3 |
 * +----+    +====+    +======+    +-----+    +======+    +-----+    +======+    +-----+
 *      claim   ^  get    |   waitFor            |   waitFor            |  waitFor
 *              |         |                      |                      |
 *              +---------+----------------------+----------------------+
 *        </pre>
 *
 * P1   - Publisher 1
 * RB   - RingBuffer
 * EPB1 - DependencyBarrier 1
 * EP1  - EventProcessor 1
 * EPB2 - DependencyBarrier 2
 * EP2  - EventProcessor 2
 * EPB3 - DependencyBarrier 3
 * EP3  - EventProcessor 3
 *
 * </pre>
 *
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class Pipeline3StepLatencyPerfTest
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 50L;
    private static final long PAUSE_NANOS = 1000L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

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

    // determine how long it takes to call System.nanoTime() (on average)
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

    private final LatencyStepQueueProcessor stepOneQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.ONE, stepOneQueue, stepTwoQueue, histogram, nanoTimeCost);
    private final LatencyStepQueueProcessor stepTwoQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.TWO, stepTwoQueue, stepThreeQueue, histogram, nanoTimeCost);
    private final LatencyStepQueueProcessor stepThreeQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.THREE, stepThreeQueue, null, histogram, nanoTimeCost);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.BUSY_SPIN);

    private final DependencyBarrier stepOneDependencyBarrier = ringBuffer.newDependencyBarrier();
    private final LatencyStepEventHandler stepOneFunctionHandler = new LatencyStepEventHandler(FunctionStep.ONE, histogram, nanoTimeCost);
    private final BatchEventProcessor<ValueEvent> stepOneBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepOneDependencyBarrier, stepOneFunctionHandler);

    private final DependencyBarrier stepTwoDependencyBarrier = ringBuffer.newDependencyBarrier(stepOneBatchProcessor);
    private final LatencyStepEventHandler stepTwoFunctionHandler = new LatencyStepEventHandler(FunctionStep.TWO, histogram, nanoTimeCost);
    private final BatchEventProcessor<ValueEvent> stepTwoBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepTwoDependencyBarrier, stepTwoFunctionHandler);

    private final DependencyBarrier stepThreeDependencyBarrier = ringBuffer.newDependencyBarrier(stepTwoBatchProcessor);
    private final LatencyStepEventHandler stepThreeFunctionHandler = new LatencyStepEventHandler(FunctionStep.THREE, histogram, nanoTimeCost);
    private final BatchEventProcessor<ValueEvent> stepThreeBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepThreeDependencyBarrier, stepThreeFunctionHandler);
    {
        ringBuffer.setTrackedProcessors(stepThreeBatchProcessor);
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
        EXECUTOR.submit(stepOneBatchProcessor);
        EXECUTOR.submit(stepTwoBatchProcessor);
        EXECUTOR.submit(stepThreeBatchProcessor);

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEvent event = ringBuffer.nextEvent();
            event.setValue(System.nanoTime());
            ringBuffer.publish(event);

            long pauseStart = System.nanoTime();
            while (PAUSE_NANOS > (System.nanoTime() -  pauseStart))
            {
                // busy spin
            }
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (stepThreeBatchProcessor.getSequence().get() < expectedSequence)
        {
            // busy spin
        }

        stepOneBatchProcessor.halt();
        stepTwoBatchProcessor.halt();
        stepThreeBatchProcessor.halt();
    }

    private void runQueuePass() throws Exception
    {
        stepThreeQueueProcessor.reset();

        Future[] futures = new Future[NUM_EVENT_PROCESSORS];
        futures[0] = EXECUTOR.submit(stepOneQueueProcessor);
        futures[1] = EXECUTOR.submit(stepTwoQueueProcessor);
        futures[2] = EXECUTOR.submit(stepThreeQueueProcessor);

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
        while (stepThreeQueueProcessor.getSequence() < expectedSequence)
        {
            // busy spin
        }

        stepOneQueueProcessor.halt();
        stepTwoQueueProcessor.halt();
        stepThreeQueueProcessor.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }
    }
}
