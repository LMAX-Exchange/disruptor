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

import com.lmax.disruptor.support.Operation;
import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.support.ValueMutationEventHandler;
import com.lmax.disruptor.support.ValueMutationQueueProcessor;
import com.lmax.disruptor.util.Util;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

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
 *
 * Queue Based:
 * ============
 *                 take
 *   put     +====+    +-----+
 *    +----->| Q1 |<---| EP1 |
 *    |      +====+    +-----+
 *    |
 * +----+    +====+    +-----+
 * | P1 |--->| Q2 |<---| EP2 |
 * +----+    +====+    +-----+
 *    |
 *    |      +====+    +-----+
 *    +----->| Q3 |<---| EP3 |
 *           +====+    +-----+
 *
 * P1  - Publisher 1
 * Q1  - Queue 1
 * Q2  - Queue 2
 * Q3  - Queue 3
 * EP1 - EventProcessor 1
 * EP2 - EventProcessor 2
 * EP3 - EventProcessor 3
 *
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
@SuppressWarnings("unchecked")
public final class MultiCast1P3CPerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_EVENT_PROCESSORS = 3;
    private static final int SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 300L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_EVENT_PROCESSORS);

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

    private final ArrayBlockingQueue<Long>[] blockingQueues = new ArrayBlockingQueue[NUM_EVENT_PROCESSORS];
    {
        blockingQueues[0] = new ArrayBlockingQueue<Long>(SIZE);
        blockingQueues[1] = new ArrayBlockingQueue<Long>(SIZE);
        blockingQueues[2] = new ArrayBlockingQueue<Long>(SIZE);
    }

    private final ValueMutationQueueProcessor[] queueProcessors = new ValueMutationQueueProcessor[NUM_EVENT_PROCESSORS];
    {
        queueProcessors[0] = new ValueMutationQueueProcessor(blockingQueues[0], Operation.ADDITION);
        queueProcessors[1] = new ValueMutationQueueProcessor(blockingQueues[1], Operation.SUBTRACTION);
        queueProcessors[2] = new ValueMutationQueueProcessor(blockingQueues[2], Operation.AND);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final SequenceBarrier sequenceBarrier = ringBuffer.newSequenceBarrier();

    private final ValueMutationEventHandler[] handlers = new ValueMutationEventHandler[NUM_EVENT_PROCESSORS];
    {
        handlers[0] = new ValueMutationEventHandler(Operation.ADDITION);
        handlers[1] = new ValueMutationEventHandler(Operation.SUBTRACTION);
        handlers[2] = new ValueMutationEventHandler(Operation.AND);
    }

    private final BatchEventProcessor[] batchEventProcessors = new BatchEventProcessor[NUM_EVENT_PROCESSORS];
    {
        batchEventProcessors[0] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[0]);
        batchEventProcessors[1] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[1]);
        batchEventProcessors[2] = new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handlers[2]);
        ringBuffer.setTrackedSequences(batchEventProcessors[0].getSequence(),
                                       batchEventProcessors[1].getSequence(),
                                       batchEventProcessors[2].getSequence());
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
    protected long runQueuePass(final int passNumber) throws InterruptedException
    {
        Future[] futures = new Future[NUM_EVENT_PROCESSORS];
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            queueProcessors[i].reset();
            futures[i] = EXECUTOR.submit(queueProcessors[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            final Long value = Long.valueOf(i);
            blockingQueues[0].put(value);
            blockingQueues[1].put(value);
            blockingQueues[2].put(value);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (getMinimumSequence(queueProcessors) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            queueProcessors[i].halt();
            futures[i].cancel(true);
            Assert.assertEquals(results[i], queueProcessors[i].getValue());
        }

        return opsPerSecond;
    }

    private long getMinimumSequence(final ValueMutationQueueProcessor[] queueProcessors)
    {
        long minimum = Long.MAX_VALUE;

        for (ValueMutationQueueProcessor processor : queueProcessors)
        {
            long sequence = processor.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }

    @Override
    protected long runDisruptorPass(final int passNumber)
    {
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            handlers[i].reset();
            EXECUTOR.submit(batchEventProcessors[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.nextSequence();
            ValueEvent event = ringBuffer.get(sequence);
            event.setValue(i);
            ringBuffer.publish(sequence);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (Util.getMinimumSequence(batchEventProcessors) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        for (int i = 0; i < NUM_EVENT_PROCESSORS; i++)
        {
            batchEventProcessors[i].halt();
            Assert.assertEquals(results[i], handlers[i].getValue());
        }

        return opsPerSecond;
    }
}
