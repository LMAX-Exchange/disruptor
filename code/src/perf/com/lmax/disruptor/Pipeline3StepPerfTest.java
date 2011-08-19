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

import com.lmax.disruptor.support.FunctionEvent;
import com.lmax.disruptor.support.FunctionEventHandler;
import com.lmax.disruptor.support.FunctionQueueProcessor;
import com.lmax.disruptor.support.FunctionStep;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

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
 */
public final class Pipeline3StepPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 300L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

    private static final long OPERAND_TWO_INITIAL_VALUE = 777L;
    private final long expectedResult;
    {
        long temp = 0L;
        long operandTwo = OPERAND_TWO_INITIAL_VALUE;

        for (long i = 0; i < ITERATIONS; i++)
        {
            long stepOneResult = i + operandTwo--;
            long stepTwoResult = stepOneResult + 3;

            if ((stepTwoResult & 4L) == 4L)
            {
                ++temp;
            }
        }

        expectedResult = temp;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<long[]> stepOneQueue = new ArrayBlockingQueue<long[]>(SIZE);
    private final BlockingQueue<Long> stepTwoQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> stepThreeQueue = new ArrayBlockingQueue<Long>(SIZE);

    private final FunctionQueueProcessor stepOneQueueProcessor =
        new FunctionQueueProcessor(FunctionStep.ONE, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueProcessor stepTwoQueueProcessor =
        new FunctionQueueProcessor(FunctionStep.TWO, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueProcessor stepThreeQueueProcessor =
        new FunctionQueueProcessor(FunctionStep.THREE, stepOneQueue, stepTwoQueue, stepThreeQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FunctionEvent> ringBuffer =
        new RingBuffer<FunctionEvent>(FunctionEvent.EVENT_FACTORY, SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final DependencyBarrier stepOneDependencyBarrier = ringBuffer.newDependencyBarrier();
    private final FunctionEventHandler stepOneFunctionHandler = new FunctionEventHandler(FunctionStep.ONE);
    private final BatchEventProcessor<FunctionEvent> stepOneBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepOneDependencyBarrier, stepOneFunctionHandler);

    private final DependencyBarrier stepTwoDependencyBarrier = ringBuffer.newDependencyBarrier(stepOneBatchProcessor);
    private final FunctionEventHandler stepTwoFunctionHandler = new FunctionEventHandler(FunctionStep.TWO);
    private final BatchEventProcessor<FunctionEvent> stepTwoBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepTwoDependencyBarrier, stepTwoFunctionHandler);

    private final DependencyBarrier stepThreeDependencyBarrier = ringBuffer.newDependencyBarrier(stepTwoBatchProcessor);
    private final FunctionEventHandler stepThreeFunctionHandler = new FunctionEventHandler(FunctionStep.THREE);
    private final BatchEventProcessor<FunctionEvent> stepThreeBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepThreeDependencyBarrier, stepThreeFunctionHandler);
    {
        ringBuffer.setTrackedProcessors(stepThreeBatchProcessor);
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
    protected long runDisruptorPass(final int passNumber)
    {
        stepThreeFunctionHandler.reset();

        EXECUTOR.submit(stepOneBatchProcessor);
        EXECUTOR.submit(stepTwoBatchProcessor);
        EXECUTOR.submit(stepThreeBatchProcessor);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            FunctionEvent event = ringBuffer.nextEvent();
            event.setOperandOne(i);
            event.setOperandTwo(operandTwo--);
            ringBuffer.publish(event);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (stepThreeBatchProcessor.getSequence().get() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneBatchProcessor.halt();
        stepTwoBatchProcessor.halt();
        stepThreeBatchProcessor.halt();

        Assert.assertEquals(expectedResult, stepThreeFunctionHandler.getStepThreeCounter());

        return opsPerSecond;
    }

    @Override
    protected long runQueuePass(final int passNumber) throws Exception
    {
        stepThreeQueueProcessor.reset();

        Future[] futures = new Future[NUM_EVENT_PROCESSORS];
        futures[0] = EXECUTOR.submit(stepOneQueueProcessor);
        futures[1] = EXECUTOR.submit(stepTwoQueueProcessor);
        futures[2] = EXECUTOR.submit(stepThreeQueueProcessor);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            long[] values = new long[2];
            values[0] = i;
            values[1] = operandTwo--;
            stepOneQueue.put(values);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (stepThreeQueueProcessor.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneQueueProcessor.halt();
        stepTwoQueueProcessor.halt();
        stepThreeQueueProcessor.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }

        Assert.assertEquals(expectedResult, stepThreeQueueProcessor.getStepThreeCounter());

        return opsPerSecond;
    }
}
