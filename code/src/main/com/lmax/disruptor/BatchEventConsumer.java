package com.lmax.disruptor;

/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the events to a {@link BatchEventHandler}.
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEventConsumer<T extends Entry>
    implements EventConsumer
{
    private volatile long sequence = -1L;
    private volatile boolean running = true;

    private final ThresholdBarrier<T> barrier;
    private final BatchEventHandler<T> handlerBatch;
    private EventExceptionHandler eventExceptionHandler = new FatalEventExceptionHandler();

    private final boolean noProgressTracker;

    /**
     * Construct a batch consumer that will automatically track the progress by updating its sequence when
     * the onEvent method returns from the delegated call to the {@link BatchEventHandler}
     *
     * @param barrier on which it is waiting.
     * @param handler is the delegate to which events are dispatched.
     */
    public BatchEventConsumer(final ThresholdBarrier<T> barrier,
                              final BatchEventHandler<T> handler)
    {
        this.barrier = barrier;
        this.handlerBatch = handler;
        this.noProgressTracker = true;
    }

    /**
     * Construct a batch consumer that will rely on the {@link ProgressReportingEventHandler}
     * to callback via the {@link BatchEventConsumer.ProgressTrackerCallback} when it has completed with a sequence.
     *
     * @param barrier on which it is waiting.
     * @param handler is the delegate to which events are dispatched.
     */
    public BatchEventConsumer(final ThresholdBarrier<T> barrier,
                              final ProgressReportingEventHandler<T> handler)
    {
        this.barrier = barrier;
        this.handlerBatch = handler;

        this.noProgressTracker = false;
        handler.setProgressTracker(new ProgressTrackerCallback());
    }

    /**
     * Set a new {@link EventExceptionHandler} for handling exceptions propagated out of the {@link BatchEventConsumer}
     *
     * @param eventExceptionHandler to replace the existing handler.
     */
    public void setEventExceptionHandler(final EventExceptionHandler eventExceptionHandler)
    {
        if (null == eventExceptionHandler)
        {
            throw new NullPointerException();
        }

        this.eventExceptionHandler = eventExceptionHandler;
    }

    @Override
    public long getSequence()
    {
        return sequence;
    }

    @Override
    public ThresholdBarrier<? extends T> getBarrier()
    {
        return barrier;
    }

    @Override
    public void halt()
    {
        running = false;
    }

    @Override
    public void run()
    {
        T entry = null;
        final Thread thisThread = Thread.currentThread();

        while (running && !thisThread.isInterrupted())
        {
            try
            {
                final long nextSequence = sequence + 1;
                final long availableSeq = barrier.waitFor(nextSequence);

                for (long i = nextSequence; i <= availableSeq; i++)
                {
                    entry = barrier.getRingBuffer().getEntry(i);
                    handlerBatch.onEvent(entry);

                    if (noProgressTracker)
                    {
                        sequence = i;
                    }
                }

                handlerBatch.onEndOfBatch();
            }
            catch (final AlertException ex)
            {
                // Wake up from blocking wait and check if we should continue to run
            }
            catch (final Exception ex)
            {
                eventExceptionHandler.handle(ex, entry);
            }
        }

        handlerBatch.onCompletion();
    }

    /**
     * Used by the {@link BatchEventConsumer} to signal when it has completed consuming a given sequence.
     */
    public final class ProgressTrackerCallback
    {
        /**
         * Signal that the sequence has been consumed.
         *
         * @param sequence that has been consumed.
         */
        public void onCompleted(final long sequence)
        {
            BatchEventConsumer.this.sequence = sequence;
        }
    }
}