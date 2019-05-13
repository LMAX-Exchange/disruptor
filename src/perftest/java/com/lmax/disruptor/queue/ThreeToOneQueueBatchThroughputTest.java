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

import com.lmax.disruptor.AbstractPerfTestQueue;
import com.lmax.disruptor.support.ValueAdditionQueueBatchProcessor;
import com.lmax.disruptor.support.ValueQueuePublisher;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.*;

/**
 * <pre>
 *
 * Sequence a series of events from multiple publishers going to one event processor.
 *
 * +----+
 * | P1 |------+
 * +----+      |
 *             v
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *             ^
 * +----+      |
 * | P3 |------+
 * +----+
 *
 *
 * Queue Based:
 * ============
 *
 * +----+  put
 * | P1 |------+
 * +----+      |
 *             v   take
 * +----+    +====+    +-----+
 * | P2 |--->| Q1 |<---| EP1 |
 * +----+    +====+    +-----+
 *             ^
 * +----+      |
 * | P3 |------+
 * +----+
 *
 * P1  - Publisher 1
 * P2  - Publisher 2
 * P3  - Publisher 3
 * Q1  - Queue 1
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class ThreeToOneQueueBatchThroughputTest extends AbstractPerfTestQueue
{
    private static final int NUM_PUBLISHERS = 3;
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 20L;
    private final ExecutorService executor = Executors.newFixedThreadPool(NUM_PUBLISHERS + 1, DaemonThreadFactory.INSTANCE);
    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PUBLISHERS + 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(BUFFER_SIZE);
    private final ValueAdditionQueueBatchProcessor queueProcessor =
        new ValueAdditionQueueBatchProcessor(blockingQueue, ((ITERATIONS / NUM_PUBLISHERS) * NUM_PUBLISHERS) - 1L);
    private final ValueQueuePublisher[] valueQueuePublishers = new ValueQueuePublisher[NUM_PUBLISHERS];

    {
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            valueQueuePublishers[i] =
                new ValueQueuePublisher(cyclicBarrier, blockingQueue, ITERATIONS / NUM_PUBLISHERS);
        }
    }

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
        queueProcessor.reset(latch);

        Future<?>[] futures = new Future[NUM_PUBLISHERS];
        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i] = executor.submit(valueQueuePublishers[i]);
        }
        Future<?> processorFuture = executor.submit(queueProcessor);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PUBLISHERS; i++)
        {
            futures[i].get();
        }

        latch.await();

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueProcessor.halt();
        processorFuture.cancel(true);

        return opsPerSecond;
    }

    public static void main(String[] args) throws Exception
    {
        new ThreeToOneQueueBatchThroughputTest().testImplementations();
    }
}
