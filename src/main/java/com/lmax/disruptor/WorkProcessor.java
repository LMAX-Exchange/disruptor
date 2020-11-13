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

import java.util.concurrent.atomic.AtomicReference;

/**
 * <p>A {@link WorkProcessor} wraps a single {@link WorkHandler}, effectively consuming the sequence
 * and ensuring appropriate barriers.</p>
 *
 * <p>Generally, this will be used as part of a {@link WorkerPool}.</p>
 *
 * @param <T> event implementation storing the details for the work to processed.
 */
public final class WorkProcessor<T>
    implements EventProcessor
{
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T> ringBuffer;
    private final SequenceBarrier sequenceBarrier;
    private final WorkHandler<? super T> workHandler;
    private final ExceptionHandler<? super T> exceptionHandler;
    private final Sequence workSequence;

    private final EventReleaser eventReleaser = () -> sequence.set(Long.MAX_VALUE);

    private final TimeoutHandler timeoutHandler;

    private enum RunState
    {
        IDLE,
        RUNNING,
        HALTED
    }

    private final AtomicReference<RunState> runState = new AtomicReference<>(RunState.IDLE);

    /**
     * Construct a {@link WorkProcessor}.
     *
     * @param ringBuffer       to which events are published.
     * @param sequenceBarrier  on which it is waiting.
     * @param workHandler      is the delegate to which events are dispatched.
     * @param exceptionHandler to be called back when an error occurs
     * @param workSequence     from which to claim the next event to be worked on.  It should always be initialised
     *                         as {@link Sequencer#INITIAL_CURSOR_VALUE}
     */
    public WorkProcessor(
        final RingBuffer<T> ringBuffer,
        final SequenceBarrier sequenceBarrier,
        final WorkHandler<? super T> workHandler,
        final ExceptionHandler<? super T> exceptionHandler,
        final Sequence workSequence)
    {
        this.ringBuffer = ringBuffer;
        this.sequenceBarrier = sequenceBarrier;
        this.workHandler = workHandler;
        this.exceptionHandler = exceptionHandler;
        this.workSequence = workSequence;

        if (this.workHandler instanceof EventReleaseAware)
        {
            ((EventReleaseAware) this.workHandler).setEventReleaser(eventReleaser);
        }

        timeoutHandler = (workHandler instanceof TimeoutHandler) ? (TimeoutHandler) workHandler : null;
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        runState.set(RunState.HALTED);
        sequenceBarrier.alert();
    }

    /**
     * remove workProcessor dynamic without message lost
     */
    public void haltLater()
    {
        runState.set(RunState.HALTED);
    }

    @Override
    public boolean isRunning()
    {
        return runState.get() == RunState.RUNNING;
    }

    /**
     * It is ok to have another thread re-run this method after a halt().
     *
     * @throws IllegalStateException if this processor is already running
     */
    @Override
    public void run()
    {
        if (!runState.compareAndSet(RunState.IDLE, RunState.RUNNING))
        {
            if (runState.get() == RunState.RUNNING)
            {
                throw new IllegalStateException("Thread is already running");
            }
            else
            {
                return;
            }
        }
        sequenceBarrier.clearAlert();

        notifyStart();

        boolean processedSequence = true;
        long cachedAvailableSequence = Long.MIN_VALUE;
        long nextSequence = sequence.get();
        T event = null;
        while (true)
        {
            try
            {
                // if previous sequence was processed - fetch the next sequence and set
                // that we have successfully processed the previous sequence
                // typically, this will be true
                // this prevents the sequence getting too far forward if an exception
                // is thrown from the WorkHandler

                if (processedSequence)
                {
                    if (runState.get() != RunState.RUNNING)
                    {
                        sequenceBarrier.alert();
                        sequenceBarrier.checkAlert();
                    }
                    processedSequence = false;
                    do
                    {
                        nextSequence = workSequence.get() + 1L;
                        sequence.set(nextSequence - 1L);
                    }
                    while (!workSequence.compareAndSet(nextSequence - 1L, nextSequence));
                }

                if (cachedAvailableSequence >= nextSequence)
                {
                    event = ringBuffer.get(nextSequence);
                    workHandler.onEvent(event);
                    processedSequence = true;
                }
                else
                {
                    cachedAvailableSequence = sequenceBarrier.waitFor(nextSequence);
                }
            }
            catch (final TimeoutException e)
            {
                notifyTimeout(sequence.get());
            }
            catch (final AlertException ex)
            {
                if (runState.get() != RunState.RUNNING)
                {
                    break;
                }
            }
            catch (final Throwable ex)
            {
                // handle, mark as processed, unless the exception handler threw an exception
                exceptionHandler.handleEventException(ex, nextSequence, event);
                processedSequence = true;
            }
        }

        notifyShutdown();

        runState.set(RunState.HALTED);
    }

    private void notifyTimeout(final long availableSequence)
    {
        try
        {
            if (timeoutHandler != null)
            {
                timeoutHandler.onTimeout(availableSequence);
            }
        }
        catch (Throwable e)
        {
            exceptionHandler.handleEventException(e, availableSequence, null);
        }
    }

    private void notifyStart()
    {
        if (workHandler instanceof LifecycleAware)
        {
            try
            {
                ((LifecycleAware) workHandler).onStart();
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
                ((LifecycleAware) workHandler).onShutdown();
            }
            catch (final Throwable ex)
            {
                exceptionHandler.handleOnShutdownException(ex);
            }
        }
    }
}
