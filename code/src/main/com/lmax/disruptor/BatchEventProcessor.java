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

/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available {@link AbstractEvent}s to a {@link BatchEventHandler}.
 *
 * If the {@link BatchEventHandler} also implements {@link LifecycleAware} it will be notified just after the thread
 * is started and just before the thread is shutdown.
 *
 * @param <T> {@link AbstractEvent} implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEventProcessor<T extends AbstractEvent>
    implements EventProcessor
{
    public long p1, p2, p3, p4, p5, p6, p7;  // cache line padding
    private volatile long sequence = RingBuffer.INITIAL_CURSOR_VALUE;
    public long p8, p9, p10, p11, p12, p13, p14; // cache line padding

    private final EventProcessorBarrier<T> eventProcessorBarrier;
    private final BatchEventHandler<T> eventHandler;
    private ExceptionHandler exceptionHandler = new FatalExceptionHandler();
    private volatile boolean running = true;

    /**
     * Construct a batch processor that will automatically track the progress by updating its sequence when
     * the {@link BatchEventHandler#onAvailable(AbstractEvent)} method returns.
     *
     * @param eventProcessorBarrier on which it is waiting.
     * @param eventHandler is the delegate to which {@link AbstractEvent}s are dispatched.
     */
    public BatchEventProcessor(final EventProcessorBarrier<T> eventProcessorBarrier,
                               final BatchEventHandler<T> eventHandler)
    {
        this.eventProcessorBarrier = eventProcessorBarrier;
        this.eventHandler = eventHandler;
    }

    /**
     * Construct a batch processor that will rely on the {@link SequenceTrackingEventHandler}
     * to callback via the {@link BatchEventProcessor.SequenceTrackerCallback} when it has
     * completed with a sequence within a batch.  Sequence will be updated at the end of
     * a batch regardless.
     *
     * @param eventProcessorBarrier on which it is waiting.
     * @param eventHandler is the delegate to which {@link AbstractEvent}s are dispatched.
     */
    public BatchEventProcessor(final EventProcessorBarrier<T> eventProcessorBarrier,
                               final SequenceTrackingEventHandler<T> eventHandler)
    {
        this.eventProcessorBarrier = eventProcessorBarrier;
        this.eventHandler = eventHandler;

        eventHandler.setSequenceTrackerCallback(new SequenceTrackerCallback());
    }

    @Override
    public long getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running = false;
        eventProcessorBarrier.alert();
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEventProcessor}
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler exceptionHandler)
    {
        if (null == exceptionHandler)
        {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
    }

    /**
     * Get the {@link EventProcessorBarrier} the {@link EventProcessor} is waiting on.
     *
      * @return the barrier this {@link EventProcessor} is using.
     */
    public EventProcessorBarrier<? extends T> getEventProcessorBarrier()
    {
        return eventProcessorBarrier;
    }

    /**
     * It is ok to have another thread rerun this method after a halt().
     */
    @Override
    public void run()
    {
        running = true;
        if (LifecycleAware.class.isAssignableFrom(eventHandler.getClass()))
        {
            ((LifecycleAware) eventHandler).onStart();
        }

        T event = null;
        long nextSequence = sequence + 1 ;
        while (running)
        {
            try
            {
                final long availableSequence = eventProcessorBarrier.waitFor(nextSequence);
                while (nextSequence <= availableSequence)
                {
                    event = eventProcessorBarrier.getEvent(nextSequence);
                    eventHandler.onAvailable(event);
                    nextSequence++;
                }

                eventHandler.onEndOfBatch();
                sequence = event.getSequence();
            }
            catch (final AlertException ex)
            {
                // Wake up from blocking wait and check if we should continue to run
            }
            catch (final Exception ex)
            {
                exceptionHandler.handle(ex, event);
                sequence = event.getSequence();
                nextSequence = event.getSequence() + 1;
            }
        }

        if (LifecycleAware.class.isAssignableFrom(eventHandler.getClass()))
        {
            ((LifecycleAware) eventHandler).onShutdown();
        }
    }

    /**
     * Used by the {@link BatchEventHandler} to signal when it has completed consuming a given sequence.
     */
    public final class SequenceTrackerCallback
    {
        /**
         * Notify that the eventHandler has consumed up to a given sequence.
         *
         * @param sequence that has been consumed.
         */
        public void onCompleted(final long sequence)
        {
            BatchEventProcessor.this.sequence = sequence;
        }
    }
}