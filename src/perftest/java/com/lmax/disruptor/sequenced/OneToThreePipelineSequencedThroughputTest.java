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
package com.lmax.disruptor.sequenced;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static com.lmax.disruptor.support.PerfTestUtil.failIfNot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.*;
import com.lmax.disruptor.support.FunctionEvent;
import com.lmax.disruptor.support.FunctionEventHandler;
import com.lmax.disruptor.support.FunctionStep;
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
 */
public final class OneToThreePipelineSequencedThroughputTest extends AbstractPerfTestDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS, DaemonThreadFactory.INSTANCE);

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

    private final RingBuffer<FunctionEvent> ringBuffer =
        createSingleProducer(FunctionEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

    private final SequenceBarrier stepOneSequenceBarrier = ringBuffer.newBarrier();
    private final FunctionEventHandler stepOneFunctionHandler = new FunctionEventHandler(FunctionStep.ONE);
    private final BatchEventProcessor<FunctionEvent> stepOneBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepOneSequenceBarrier, stepOneFunctionHandler);

    private final SequenceBarrier stepTwoSequenceBarrier = ringBuffer.newBarrier(stepOneBatchProcessor.getSequence());
    private final FunctionEventHandler stepTwoFunctionHandler = new FunctionEventHandler(FunctionStep.TWO);
    private final BatchEventProcessor<FunctionEvent> stepTwoBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepTwoSequenceBarrier, stepTwoFunctionHandler);

    private final SequenceBarrier stepThreeSequenceBarrier = ringBuffer.newBarrier(stepTwoBatchProcessor.getSequence());
    private final FunctionEventHandler stepThreeFunctionHandler = new FunctionEventHandler(FunctionStep.THREE);
    private final BatchEventProcessor<FunctionEvent> stepThreeBatchProcessor =
        new BatchEventProcessor<FunctionEvent>(ringBuffer, stepThreeSequenceBarrier, stepThreeFunctionHandler);

    {
        ringBuffer.addGatingSequences(stepThreeBatchProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 4;
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws InterruptedException
    {
        PerfTestContext perfTestContext = new PerfTestContext();

        CountDownLatch latch = new CountDownLatch(1);
        stepThreeFunctionHandler.reset(latch, stepThreeBatchProcessor.getSequence().get() + ITERATIONS);

        executor.submit(stepOneBatchProcessor);
        executor.submit(stepTwoBatchProcessor);
        executor.submit(stepThreeBatchProcessor);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            FunctionEvent event = ringBuffer.get(sequence);
            event.setOperandOne(i);
            event.setOperandTwo(operandTwo--);
            ringBuffer.publish(sequence);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));

        stepOneBatchProcessor.halt();
        stepTwoBatchProcessor.halt();
        stepThreeBatchProcessor.halt();

        failIfNot(expectedResult, stepThreeFunctionHandler.getStepThreeCounter());

        return perfTestContext;
    }

    public static void main(String[] args) throws Exception
    {
        new OneToThreePipelineSequencedThroughputTest().testImplementations();
    }
}
