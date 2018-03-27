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
import com.lmax.disruptor.support.FizzBuzzEvent;
import com.lmax.disruptor.support.FizzBuzzEventHandler;
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
public final class OneToThreeDiamondSequencedThroughputTest extends AbstractPerfTestDisruptor
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

    private final RingBuffer<FizzBuzzEvent> ringBuffer =
        createSingleProducer(FizzBuzzEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

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
        ringBuffer.addGatingSequences(batchProcessorFizzBuzz.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 4;
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws Exception
    {
        PerfTestContext perfTestContext = new PerfTestContext();
        CountDownLatch latch = new CountDownLatch(1);
        fizzBuzzHandler.reset(latch, batchProcessorFizzBuzz.getSequence().get() + ITERATIONS);

        executor.submit(batchProcessorFizz);
        executor.submit(batchProcessorBuzz);
        executor.submit(batchProcessorFizzBuzz);

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setValue(i);
            ringBuffer.publish(sequence);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));

        batchProcessorFizz.halt();
        batchProcessorBuzz.halt();
        batchProcessorFizzBuzz.halt();

        failIfNot(expectedResult, fizzBuzzHandler.getFizzBuzzCounter());

        return perfTestContext;
    }

    public static void main(String[] args) throws Exception
    {
        new OneToThreeDiamondSequencedThroughputTest().testImplementations();
    }
}
