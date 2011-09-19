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

import com.lmax.disruptor.support.*;
import com.lmax.disruptor.util.PaddedLong;
import org.junit.Test;

import java.util.concurrent.*;

import static junit.framework.Assert.assertEquals;

public final class OnePublisherToThreeWorkerPoolThroughputTest
    extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_WORKERS = 3;
    private static final int SIZE = 1024 * 8;
    private static final long ITERATIONS = 1000L * 1000L * 300L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_WORKERS);

    private final PaddedLong[] counters = new PaddedLong[NUM_WORKERS];
    {
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            counters[i] = new PaddedLong();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final EventCountingQueueProcessor[] queueWorkers = new EventCountingQueueProcessor[NUM_WORKERS];
    {
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            queueWorkers[i] = new EventCountingQueueProcessor(blockingQueue, counters, i);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> ringBuffer =
        new RingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final EventCountingWorkHandler[] handlers = new EventCountingWorkHandler[NUM_WORKERS];
    {
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            handlers[i] = new EventCountingWorkHandler(counters, i);
        }
    }

    private final WorkerPool<ValueEvent> workerPool = new WorkerPool<ValueEvent>(ringBuffer, handlers);
    {
        ringBuffer.setGatingSequences(workerPool.getWorkerSequences());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass(final int passNumber) throws InterruptedException
    {
        resetCounters();
        Future[] futures = new Future[NUM_WORKERS];
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            futures[i] = EXECUTOR.submit(queueWorkers[i]);
        }

        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
        }

        while (blockingQueue.size() > 0)
        {
            // spin while queue drains
        }

        for (int i = 0; i < NUM_WORKERS; i++)
        {
            queueWorkers[i].halt();
            futures[i].cancel(true);
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        assertEquals(ITERATIONS, sumCounters());

        return opsPerSecond;
    }

    @Override
    protected long runDisruptorPass(final int passNumber) throws InterruptedException
    {
        resetCounters();
        workerPool.start(EXECUTOR);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            ringBuffer.get(sequence).setValue(i);
            ringBuffer.publish(sequence);
        }

        workerPool.drainAndHalt();
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        assertEquals(ITERATIONS, sumCounters());

        return opsPerSecond;
    }

    private void resetCounters()
    {
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            counters[i].set(0L);
        }
    }

    private long sumCounters()
    {
        long sumJobs = 0L;
        for (int i = 0; i < NUM_WORKERS; i++)
        {
            sumJobs += counters[i].get();
        }

        return sumJobs;
    }
}
