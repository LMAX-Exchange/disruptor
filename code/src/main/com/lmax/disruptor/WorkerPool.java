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
import com.lmax.disruptor.util.Util;

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
    private final WorkProcessor<?>[] workProcessors;

    /**
     * Create a worker pool to enable an array of {@link WorkHandler}s to consume published sequences.
     *
     * This option requires a pre-configured {@link RingBuffer} which must have {@link RingBuffer#setGatingSequences(Sequence...)}
     * called before the work pool is started.
     *
     * @param ringBuffer of events to be consumed.
     * @param sequenceBarrier on which the workers will depend.
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers to distribute the work load across.
     */
    public WorkerPool(final RingBuffer<T> ringBuffer,
                      final SequenceBarrier sequenceBarrier,
                      final ExceptionHandler exceptionHandler,
                      final WorkHandler<T>... workHandlers)
    {
        this.ringBuffer = ringBuffer;
        final int numWorkers = workHandlers.length;
        workProcessors = new WorkProcessor[numWorkers];

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
     * Construct a work pool with an internal {@link RingBuffer} for convenience.
     *
     * This option does not require {@link RingBuffer#setGatingSequences(Sequence...)} to be called before the work pool is started.
     *
     * @param eventFactory for filling the {@link RingBuffer}
     * @param claimStrategy for the {@link RingBuffer}
     * @param waitStrategy for the {@link RingBuffer}
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers to distribute the work load across.
     */
    public WorkerPool(final EventFactory<T> eventFactory,
                      final ClaimStrategy claimStrategy,
                      final WaitStrategy waitStrategy,
                      final ExceptionHandler exceptionHandler,
                      final WorkHandler<T>... workHandlers)
    {
        ringBuffer = new RingBuffer<T>(eventFactory, claimStrategy, waitStrategy);
        final SequenceBarrier barrier = ringBuffer.newBarrier();
        final int numWorkers = workHandlers.length;
        workProcessors = new WorkProcessor[numWorkers];

        for (int i = 0; i < numWorkers; i++)
        {
            workProcessors[i] = new WorkProcessor<T>(ringBuffer,
                                                     barrier,
                                                     workHandlers[i],
                                                     exceptionHandler,
                                                     workSequence);
        }

        ringBuffer.setGatingSequences(getWorkerSequences());
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
     * @return the {@link RingBuffer} used for the work queue.
     * @throws IllegalStateException is the pool has already been started and not halted yet
     */
    public RingBuffer<T> start(final Executor executor)
    {
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("WorkerPool has already been started and cannot be restarted until halted.");
        }

        final long cursor = ringBuffer.getCursor();
        workSequence.set(cursor);

        for (WorkProcessor<?> processor : workProcessors)
        {
            processor.getSequence().set(cursor);
            executor.execute(processor);
        }

        return ringBuffer;
    }

    /**
     * Wait for the {@link RingBuffer} to drain of published events then halt the workers.
     */
    public void drainAndHalt()
    {
        Sequence[] workerSequences = getWorkerSequences();
        while (ringBuffer.getCursor() > Util.getMinimumSequence(workerSequences))
        {
            Thread.yield();
        }

        for (WorkProcessor<?> processor : workProcessors)
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
        for (WorkProcessor<?> processor : workProcessors)
        {
            processor.halt();
        }

        started.set(false);
    }
}
