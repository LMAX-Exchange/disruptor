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
import com.lmax.disruptor.support.ValueAdditionQueueProcessor;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.*;

import static com.lmax.disruptor.support.PerfTestUtil.failIf;

/**
 * <pre>
 * UniCast a series of items between 1 publisher and 1 event processor.
 *
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *
 * Queue Based:
 * ============
 *
 *        put      take
 * +----+    +====+    +-----+
 * | P1 |--->| Q1 |<---| EP1 |
 * +----+    +====+    +-----+
 *
 * P1  - Publisher 1
 * Q1  - Queue 1
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class OneToOneQueueThroughputTest extends AbstractPerfTestQueue
{
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 10L;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final long expectedResult = ITERATIONS * 3L;

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final ValueAdditionQueueProcessor queueProcessor =
        new ValueAdditionQueueProcessor(blockingQueue, ITERATIONS - 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    @Override
    protected long runQueuePass() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        queueProcessor.reset(latch);
        Future<?> future = executor.submit(queueProcessor);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(3L);
        }

        latch.await();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueProcessor.halt();
        future.cancel(true);

        failIf(expectedResult, 0);

        return opsPerSecond;
    }

    public static void main(String[] args) throws Exception
    {
        OneToOneQueueThroughputTest test = new OneToOneQueueThroughputTest();
        test.testImplementations();
    }
}
