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

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static java.lang.Math.max;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.HdrHistogram.Histogram;
import org.junit.Test;

import com.lmax.disruptor.support.FunctionStep;
import com.lmax.disruptor.support.LatencyStepEventHandler;
import com.lmax.disruptor.support.LatencyStepQueueProcessor;
import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

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
 *              +----------------------------------------------------------------+
 *              |                                                                |
 *              |                                                                v
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 * | P1 |--->| RB |    | SB1 |<---| EP1 |<---| SB2 |<---| EP2 |<---| SB3 |<---| EP3 |
 * +----+    +====+    +=====+    +-----+    +=====+    +-----+    +=====+    +-----+
 *      claim   ^  get    |   waitFor           |   waitFor           |  waitFor
 *              |         |                     |                     |
 *              +---------+---------------------+---------------------+
 *        </pre>
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB1 - SequenceBarrier 1
 * EP1 - EventProcessor 1
 * SB2 - SequenceBarrier 2
 * EP2 - EventProcessor 2
 * SB3 - SequenceBarrier 3
 * EP3 - EventProcessor 3
 *
 * </pre>
 *
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class ThrottledOnePublisherToThreeProcessorPipelineLatencyTest
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int BUFFER_SIZE = 1024;
    private static final long ITERATIONS = 1000L * 1000L * 30L;
    private static final long PAUSE_NANOS = 1000L;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS, DaemonThreadFactory.INSTANCE);

    private final Histogram histogram = new Histogram(10000000000L, 4);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> stepOneQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> stepTwoQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> stepThreeQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);

    private final LatencyStepQueueProcessor stepOneQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.ONE, stepOneQueue, stepTwoQueue, ITERATIONS - 1);
    private final LatencyStepQueueProcessor stepTwoQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.TWO, stepTwoQueue, stepThreeQueue, ITERATIONS - 1);
    private final LatencyStepQueueProcessor stepThreeQueueProcessor =
        new LatencyStepQueueProcessor(FunctionStep.THREE, stepThreeQueue, null, ITERATIONS - 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

    private final SequenceBarrier stepOneSequenceBarrier = ringBuffer.newBarrier();
    private final LatencyStepEventHandler stepOneFunctionHandler = new LatencyStepEventHandler(FunctionStep.ONE);
    private final BatchEventProcessor<ValueEvent> stepOneBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepOneSequenceBarrier, stepOneFunctionHandler);

    private final SequenceBarrier stepTwoSequenceBarrier = ringBuffer.newBarrier(stepOneBatchProcessor.getSequence());
    private final LatencyStepEventHandler stepTwoFunctionHandler = new LatencyStepEventHandler(FunctionStep.TWO);
    private final BatchEventProcessor<ValueEvent> stepTwoBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepTwoSequenceBarrier, stepTwoFunctionHandler);

    private final SequenceBarrier stepThreeSequenceBarrier = ringBuffer.newBarrier(stepTwoBatchProcessor.getSequence());
    private final LatencyStepEventHandler stepThreeFunctionHandler = new LatencyStepEventHandler(FunctionStep.THREE);
    private final BatchEventProcessor<ValueEvent> stepThreeBatchProcessor =
        new BatchEventProcessor<ValueEvent>(ringBuffer, stepThreeSequenceBarrier, stepThreeFunctionHandler);
    {
        ringBuffer.addGatingSequences(stepThreeBatchProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        final int runs = 3;

        double[] queueMeanLatency = new double[runs];
        double[] disruptorMeanLatency = new double[runs];

        if ("true".equalsIgnoreCase(System.getProperty("com.lmax.runQueueTests", "true")))
        {
            for (int i = 0; i < runs; i++)
            {
                System.gc();
                histogram.reset();

                runQueuePass();

                assertTrue(histogram.getHistogramData().getTotalCount() >= ITERATIONS);
                queueMeanLatency[i] = histogram.getHistogramData().getMean();

                System.out.format("%s run %d BlockingQueue %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
                dumpHistogram(histogram, System.out);
            }
        }
        else
        {
            for (int i = 0; i < runs; i++)
            {
                queueMeanLatency[i] = Double.MAX_VALUE;
            }
        }

        for (int i = 0; i < runs; i++)
        {
            System.gc();
            histogram.reset();

            runDisruptorPass();

            assertTrue(histogram.getHistogramData().getTotalCount() >= ITERATIONS);
            disruptorMeanLatency[i] = histogram.getHistogramData().getMean();

            System.out.format("%s run %d Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(histogram, System.out);
        }

        for (int i = 0; i < runs; i++)
        {
            assertTrue("run: " + i, queueMeanLatency[i] > disruptorMeanLatency[i]);
        }
    }

    private static void dumpHistogram(Histogram histogram, final PrintStream out)
    {
        histogram.getHistogramData().outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runQueuePass() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        stepThreeQueueProcessor.reset(latch);

        Future<?>[] futures = new Future[NUM_EVENT_PROCESSORS];
        futures[0] = executor.submit(stepOneQueueProcessor);
        futures[1] = executor.submit(stepTwoQueueProcessor);
        futures[2] = executor.submit(stepThreeQueueProcessor);

        Thread.sleep(1000);

        Sequence sequence = stepThreeQueueProcessor.getSequence();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long t0 = System.nanoTime();
            stepOneQueue.put(Long.valueOf(i));

            while (sequence.get() < i)
            {
                // busy spin
            }

            long t1 = System.nanoTime();
            histogram.recordValue(calculateLatency(t0, t1), PAUSE_NANOS);

            while (PAUSE_NANOS > (System.nanoTime() - t1))
            {
                Thread.yield();
                // busy spin
            }
        }

        latch.await();
        stepOneQueueProcessor.halt();
        stepTwoQueueProcessor.halt();
        stepThreeQueueProcessor.halt();

        for (Future<?> future : futures)
        {
            future.cancel(true);
        }
    }

    private void runDisruptorPass() throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);
        stepThreeFunctionHandler.reset(latch, stepThreeBatchProcessor.getSequence().get() + ITERATIONS);

        executor.submit(stepOneBatchProcessor);
        executor.submit(stepTwoBatchProcessor);
        executor.submit(stepThreeBatchProcessor);
        Sequence stepThreeSequence = stepThreeBatchProcessor.getSequence();

        Thread.sleep(1000);

        for (long i = 0; i < ITERATIONS; i++)
        {
            long t0 = System.nanoTime();
            long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setValue(t0);
            ringBuffer.publish(sequence);

            while (stepThreeSequence.get() < sequence)
            {
                // busy spin
            }

            long t1 = System.nanoTime();
            histogram.recordValue(calculateLatency(t0, t1), PAUSE_NANOS);

            while (PAUSE_NANOS > (System.nanoTime() - t1))
            {
                // busy spin
            }
        }

        if (stepThreeSequence.get() == 0)
        {
            throw new IllegalStateException("Prevent hotspot optimising everything away");
        }

        latch.await();
        stepOneBatchProcessor.halt();
        stepTwoBatchProcessor.halt();
        stepThreeBatchProcessor.halt();
    }

    private long calculateLatency(long t0, long t1)
    {
        return max(t1 - t0, 0);
    }

    public static void main(String[] args) throws Exception
    {
        ThrottledOnePublisherToThreeProcessorPipelineLatencyTest test = new ThrottledOnePublisherToThreeProcessorPipelineLatencyTest();
        test.shouldCompareDisruptorVsQueues();
    }
}
