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

import com.lmax.disruptor.util.PaddedAtomicLong;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A pool of {@link WorkProcessor}s that will consume sequences so jobs can be farmed out across a pool of workers
 * which are implemented the {@link WorkHandler} interface.
 *
 * @param <T> event to be processed by a pool of workers
 */
public final class WorkerPool<T>
{
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final PaddedAtomicLong workSequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T> ringBuffer;
    private final WorkProcessor[] workProcessors;

    /**
     * Create a worker pool to enable an array of {@link WorkHandler}s to consume published sequences.
     *
     * @param ringBuffer of events to be consumed.
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers to distribute the work load across.
     */
    public WorkerPool(final RingBuffer<T> ringBuffer,
                      final ExceptionHandler exceptionHandler,
                      final WorkHandler<T>... workHandlers)
    {
        this.ringBuffer = ringBuffer;
        final int numWorkers = workHandlers.length;
        workProcessors = new WorkProcessor[numWorkers];
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        for (int i = 0; i < numWorkers; i++)
        {
            workProcessors[i] = new WorkProcessor<T>(ringBuffer,
                                                     sequenceBarrier,
                                                     workHandlers[i],
                                                     exceptionHandler,
                                                     workSequence);
        }
    }

    /**
     * Create a worker pool to enable an array of {@link WorkHandler}s to consume published sequences.
     *
     * This pool uses the default {@link FatalExceptionHandler}.
     *
     * @param ringBuffer of events to be consumed.
     * @param workHandlers to distribute the work load across.
     */
    public WorkerPool(final RingBuffer<T> ringBuffer, final WorkHandler<T>... workHandlers)
    {
        this(ringBuffer, new FatalExceptionHandler(), workHandlers);
    }

    /**
     * Get an array of {@link Sequence}s representing the progress of the workers.
     *
     * @return an array of {@link Sequence}s representing the progress of the workers.
     */
    public Sequence[] getWorkerSequences()
    {
        final Sequence[] sequences = new Sequence[workProcessors.length];
        for (int i = 0, size = workProcessors.length; i < size; i++)
        {
            sequences[i] = workProcessors[i].getSequence();
        }

        return sequences;
    }

    /**
     * Start the worker pool processing events in sequence.
     *
     * @param executor providing threads for running the workers.
     * @throws IllegalStateException is the pool has already been started and not halted yet.
     */
    public void start(final Executor executor)
    {
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("WorkerPool has already been started and cannot be restarted until halted.");
        }

        final long cursor = ringBuffer.getCursor();
        workSequence.set(cursor);

        for (WorkProcessor processor : workProcessors)
        {
            processor.getSequence().set(cursor);
            executor.execute(processor);
        }
    }

    /**
     * Wait for the {@link RingBuffer} to drain of published events then halt the workers.
     */
    public void drainAndHalt()
    {
        while (ringBuffer.getCursor() > workSequence.get())
        {
            Thread.yield();
        }

        for (WorkProcessor processor : workProcessors)
        {
            processor.halt();
        }

        started.set(false);
    }

    /**
     * Halt all workers immediately at then end of their current cycle.
     */
    public void halt()
    {
        for (WorkProcessor processor : workProcessors)
        {
            processor.halt();
        }

        started.set(false);
    }
}
