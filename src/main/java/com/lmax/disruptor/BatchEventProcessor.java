/*
 * Copyright 2022 LMAX Ltd.
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

import java.util.concurrent.atomic.AtomicInteger;

import static com.lmax.disruptor.RewindAction.REWIND;
import static java.lang.Math.min;


/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available events to an {@link EventHandler}.
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEventProcessor<T>
        implements EventProcessor
{
    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    private final AtomicInteger running = new AtomicInteger(IDLE);
    private ExceptionHandler<? super T> exceptionHandler;
    private final DataProvider<T> dataProvider;
    private final SequenceBarrier sequenceBarrier;
    private final EventHandlerBase<? super T> eventHandler;
    private final int batchLimitOffset;
    private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RewindHandler rewindHandler;
    private int retriesAttempted = 0;

    BatchEventProcessor(
            final DataProvider<T> dataProvider,
            final SequenceBarrier sequenceBarrier,
            final EventHandlerBase<? super T> eventHandler,
            final int maxBatchSize,
            final BatchRewindStrategy batchRewindStrategy
    )
    {
        this.dataProvider = dataProvider;
        this.sequenceBarrier = sequenceBarrier;
        this.eventHandler = eventHandler;

        if (maxBatchSize < 1)
        {
            throw new IllegalArgumentException("maxBatchSize must be greater than 0");
        }
        this.batchLimitOffset = maxBatchSize - 1;

        this.rewindHandler = eventHandler instanceof RewindableEventHandler
                ? new TryRewindHandler(batchRewindStrategy)
                : new NoRewindHandler();
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(HALTED);
        sequenceBarrier.alert();
    }

    @Override
    public boolean isRunning()
    {
        return running.get() != IDLE;
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEventProcessor}.
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler<? super T> exceptionHandler)
    {
        if (null == exceptionHandler)
        {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }

    /**
     * It is ok to have another thread rerun this method after a halt().
     *
     * @throws IllegalStateException if this object instance is already running in a thread
     */
    @Override
    public void run()
    {
        int witnessValue = running.compareAndExchange(IDLE, RUNNING);
        if (witnessValue == IDLE) // Successful CAS
        {
            sequenceBarrier.clearAlert();

            notifyStart();
            try
            {
                if (running.get() == RUNNING)
                {
                    processEvents();
                }
            }
            finally
            {
                notifyShutdown();
                running.set(IDLE);
            }
        }
        else
        {
            if (witnessValue == RUNNING)
            {
                throw new IllegalStateException("Thread is already running");
            }
            else
            {
                earlyExit();
            }
        }
    }

    private void processEvents()
    {
        T event = null;
        long nextSequence = sequence.get() + 1L;

        while (true)
        {
            final long startOfBatchSequence = nextSequence;
            try
            {
                try
                {
                    final long availableSequence = sequenceBarrier.waitFor(nextSequence);
                    final long endOfBatchSequence = min(nextSequence + batchLimitOffset, availableSequence);

                    if (nextSequence <= endOfBatchSequence)
                    {
                        eventHandler.onBatchStart(endOfBatchSequence - nextSequence + 1, availableSequence - nextSequence + 1);
                    }

                    while (nextSequence <= endOfBatchSequence)
                    {
                        event = dataProvider.get(nextSequence);
                        eventHandler.onEvent(event, nextSequence, nextSequence == endOfBatchSequence);
                        nextSequence++;
                    }

                    retriesAttempted = 0;

                    sequence.set(endOfBatchSequence);
                }
                catch (final RewindableException e)
                {
                    nextSequence = rewindHandler.attemptRewindGetNextSequence(e, startOfBatchSequence);
                }
            }
            catch (final TimeoutException e)
            {
                notifyTimeout(sequence.get());
            }
            catch (final AlertException ex)
            {
                if (running.get() != RUNNING)
                {
                    break;
                }
            }
            catch (final Throwable ex)
            {
                handleEventException(ex, nextSequence, event);
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    private void earlyExit()
    {
        notifyStart();
        notifyShutdown();
    }

    private void notifyTimeout(final long availableSequence)
    {
        try
        {
            eventHandler.onTimeout(availableSequence);
        }
        catch (Throwable e)
        {
            handleEventException(e, availableSequence, null);
        }
    }

    /**
     * Notifies the EventHandler when this processor is starting up.
     */
    private void notifyStart()
    {
        try
        {
            eventHandler.onStart();
        }
        catch (final Throwable ex)
        {
            handleOnStartException(ex);
        }
    }

    /**
     * Notifies the EventHandler immediately prior to this processor shutting down.
     */
    private void notifyShutdown()
    {
        try
        {
            eventHandler.onShutdown();
        }
        catch (final Throwable ex)
        {
            handleOnShutdownException(ex);
        }
    }

    /**
     * Delegate to {@link ExceptionHandler#handleEventException(Throwable, long, Object)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleEventException(final Throwable ex, final long sequence, final T event)
    {
        getExceptionHandler().handleEventException(ex, sequence, event);
    }

    /**
     * Delegate to {@link ExceptionHandler#handleOnStartException(Throwable)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleOnStartException(final Throwable ex)
    {
        getExceptionHandler().handleOnStartException(ex);
    }

    /**
     * Delegate to {@link ExceptionHandler#handleOnShutdownException(Throwable)} on the delegate or
     * the default {@link ExceptionHandler} if one has not been configured.
     */
    private void handleOnShutdownException(final Throwable ex)
    {
        getExceptionHandler().handleOnShutdownException(ex);
    }

    private ExceptionHandler<? super T> getExceptionHandler()
    {
        ExceptionHandler<? super T> handler = exceptionHandler;
        return handler == null ? ExceptionHandlers.defaultHandler() : handler;
    }

    private class TryRewindHandler implements RewindHandler
    {
        private final BatchRewindStrategy batchRewindStrategy;

        TryRewindHandler(final BatchRewindStrategy batchRewindStrategy)
        {
            this.batchRewindStrategy = batchRewindStrategy;
        }

        @Override
        public long attemptRewindGetNextSequence(final RewindableException e, final long startOfBatchSequence) throws RewindableException
        {
            if (batchRewindStrategy.handleRewindException(e, ++retriesAttempted) == REWIND)
            {
                return startOfBatchSequence;
            }
            else
            {
                retriesAttempted = 0;
                throw e;
            }
        }
    }

    private static class NoRewindHandler implements RewindHandler
    {
        @Override
        public long attemptRewindGetNextSequence(final RewindableException e, final long startOfBatchSequence)
        {
            throw new UnsupportedOperationException("Rewindable Exception thrown from a non-rewindable event handler", e);
        }
    }
}