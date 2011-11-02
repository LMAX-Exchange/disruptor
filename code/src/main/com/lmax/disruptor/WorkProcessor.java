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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link WorkProcessor} for ensuring each sequence is handled by only a single processor, effectively consuming the sequence.
 *
 * No other {@link WorkProcessor}s in the {@link WorkerPool} will consume the same sequence.
 *
 * @param <T> event implementation storing the details for the work to processed.
 */
public final class WorkProcessor<T>
    implements EventProcessor
{
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T> ringBuffer;
    private final SequenceBarrier sequenceBarrier;
    private final WorkHandler<T> workHandler;
    private final ExceptionHandler exceptionHandler;
    private final AtomicLong workSequence;

    /**
     * Construct a {@link WorkProcessor}.
     *
     * @param ringBuffer to which events are published.
     * @param sequenceBarrier on which it is waiting.
     * @param workHandler is the delegate to which events are dispatched.
     * @param exceptionHandler to be called back when an error occurs
     * @param workSequence from which to claim the next event to be worked on.  It should always be initialised
     * as {@link Sequencer#INITIAL_CURSOR_VALUE}
     */
    public WorkProcessor(final RingBuffer<T> ringBuffer,
                         final SequenceBarrier sequenceBarrier,
                         final WorkHandler<T> workHandler,
                         final ExceptionHandler exceptionHandler,
                         final AtomicLong workSequence)
    {
        this.ringBuffer = ringBuffer;
        this.sequenceBarrier = sequenceBarrier;
        this.workHandler = workHandler;
        this.exceptionHandler = exceptionHandler;
        this.workSequence = workSequence;
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(false);
        sequenceBarrier.alert();
    }

    /**
     * It is ok to have another thread re-run this method after a halt().
     */
    @Override
    public void run()
    {
        if (!running.compareAndSet(false, true))
        {
            throw new IllegalStateException("Thread is already running");
        }
        sequenceBarrier.clearAlert();

        notifyStart();

        boolean processedSequence = true;
        long nextSequence = sequence.get();
        T event = null;
        while (true)
        {
            try
            {
                if (processedSequence)
                {
                    processedSequence = false;
                    nextSequence = workSequence.incrementAndGet();
                    sequence.set(nextSequence - 1L);
                }

                sequenceBarrier.waitFor(nextSequence);
                event = ringBuffer.get(nextSequence);
                workHandler.onEvent(event);

                processedSequence = true;
            }
            catch (final AlertException ex)
            {
                if (!running.get())
                {
                    break;
                }
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleEventException(ex, nextSequence, event);
                processedSequence = true;
            }
        }

        notifyShutdown();

        running.set(false);
    }

    private void notifyStart()
    {
        if (workHandler instanceof LifecycleAware)
        {
            try
            {
                ((LifecycleAware)workHandler).onStart();
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleOnStartException(ex);
            }
        }
    }

    private void notifyShutdown()
    {
        if (workHandler instanceof LifecycleAware)
        {
            try
            {
                ((LifecycleAware)workHandler).onShutdown();
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}
