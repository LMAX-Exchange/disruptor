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
import com.lmax.disruptor.support.Operation;
import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.support.ValueMutationEventHandler;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * <pre>
 *
 * MultiCast a series of items between 1 publisher and 3 event processors.
 *
 *           +-----+
 *    +----->| EP1 |
 *    |      +-----+
 *    |
 * +----+    +-----+
 * | P1 |--->| EP2 |
 * +----+    +-----+
 *    |
 *    |      +-----+
 *    +----->| EP3 |
 *           +-----+
 *
 * Disruptor:
 * ==========
 *                             track to prevent wrap
 *             +--------------------+----------+----------+
 *             |                    |          |          |
 *             |                    v          v          v
 * +----+    +====+    +====+    +-----+    +-----+    +-----+
 * | P1 |--->| RB |<---| SB |    | EP1 |    | EP2 |    | EP3 |
 * +----+    +====+    +====+    +-----+    +-----+    +-----+
 *      claim      get    ^         |          |          |
 *                        |         |          |          |
 *                        +---------+----------+----------+
 *                                      waitFor
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB  - SequenceBarrier
 * EP1 - EventProcessor 1
 * EP2 - EventProcessor 2
 * EP3 - EventProcessor 3
 *
 * </pre>
 */
public final class OneToThreeSequencedThroughputTest extends AbstractPerfTestDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS, DaemonThreadFactory.INSTANCE);

    private final long[] results = new long[NUM_EVENT_PROCESSORS];

    {
        for (long i = 0; i < ITERATIONS; i++)
        {
            results[0] = Operation.ADDITION.op(results[0], i);
            results[1] = Operation.SUBTRACTION.op(results[1], i);
            results[2] = Operation.AND.op(results[2], i);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

    private final ValueMutationEventHandler[] handlers = new ValueMutationEventHandler[NUM_EVENT_PROCESSORS];

    {
        handlers[0] = new ValueMutationEventHandler(Operation.ADDITION);
        handlers[1] = new ValueMutationEventHandler(Operation.SUBTRACTION);
        handlers[2] = new ValueMutationEventHandler(Operation.AND);
    }

    private final BatchEventProcessor<?>[] batchEventProcessors = new BatchEventProcessor[NUM_EVENT_PROCESSORS];

    {
        batchEventProcessors[0] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[0]);
        batchEventProcessors[1] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[1]);
        batchEventProcessors[2] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[2]);

        ringBuffer.addGatingSequences(
            batchEventProcessors[0].getSequence(),
            batchEventProcessors[1].getSequence(),
            batchEventProcessors[2].getSequence());
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
        CountDownLatch latch = new CountDownLatch(NUM_EVENT_PROCESSORS);
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            handlers[i].reset(latch, batchEventProcessors[i].getSequence().get() + ITERATIONS);
            executor.submit(batchEventProcessors[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setValue(i);
            ringBuffer.publish(sequence);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        perfTestContext.setBatchData(sumBatches(handlers), ITERATIONS * handlers.length);
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            batchEventProcessors[i].halt();
            failIfNot(results[i], handlers[i].getValue());
        }

        return perfTestContext;
    }

    private long sumBatches(ValueMutationEventHandler[] handlers)
    {
        long sum = 0;
        for (ValueMutationEventHandler handler : handlers)
        {
            sum += handler.getBatchesProcessed();
        }
        return sum;
    }

    public static void main(String[] args) throws Exception
    {
        new OneToThreeSequencedThroughputTest().testImplementations();
    }
}
