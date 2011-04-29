package com.lmax.disruptor;

/**
 * Convenience class for handling the batching semantics of consuming entries from a {@link RingBuffer}
 * and delegating the available {@link Entry}s to a {@link BatchEntryHandler}.
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class BatchEntryConsumer<T extends Entry>
    implements EntryConsumer
{
    private volatile long sequence = -1L;
    private volatile boolean running = true;

    private final ThresholdBarrier<T> barrier;
    private final BatchEntryHandler<T> handlerBatch;
    private ExceptionHandler exceptionHandler = new FatalExceptionHandler();

    private final boolean noProgressTracker;

    /**
     * Construct a batch consumer that will automatically track the progress by updating its sequence when
     * the onAvailable method returns from the delegated call to the {@link BatchEntryHandler}
     *
     * @param barrier on which it is waiting.
     * @param handler is the delegate to which {@link Entry}s are dispatched.
     */
    public BatchEntryConsumer(final ThresholdBarrier<T> barrier,
                              final BatchEntryHandler<T> handler)
    {
        this.barrier = barrier;
        this.handlerBatch = handler;
        this.noProgressTracker = true;
    }

    /**
     * Construct a batch consumer that will rely on the {@link ProgressReportingEntryHandler}
     * to callback via the {@link BatchEntryConsumer.ProgressTrackerCallback} when it has completed with a sequence.
     *
     * @param barrier on which it is waiting.
     * @param handler is the delegate to which {@link Entry}s are dispatched.
     */
    public BatchEntryConsumer(final ThresholdBarrier<T> barrier,
                              final ProgressReportingEntryHandler<T> handler)
    {
        this.barrier = barrier;
        this.handlerBatch = handler;

        this.noProgressTracker = false;
        handler.setProgressTracker(new ProgressTrackerCallback());
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEntryConsumer}
     *
     * @param exceptionHandler to replace the existing handler.
     */
    public void setExceptionHandler(final ExceptionHandler exceptionHandler)
    {
        if (null == exceptionHandler)
        {
            throw new NullPointerException();
        }

        this.exceptionHandler = exceptionHandler;
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
                    handlerBatch.onAvailable(entry);

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
                exceptionHandler.handle(ex, entry);
            }
        }

        handlerBatch.onCompletion();
    }

    /**
     * Used by the {@link BatchEntryConsumer} to signal when it has completed consuming a given sequence.
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
            BatchEntryConsumer.this.sequence = sequence;
        }
    }
}