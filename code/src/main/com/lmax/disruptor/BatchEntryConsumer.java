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
    private volatile boolean running = true;
    private volatile long sequence = -1L;

    private final ConsumerBarrier<T> consumerBarrier;
    private final BatchEntryHandler<T> entryHandler;
    private final boolean noSequenceTracker;
    private ExceptionHandler exceptionHandler = new FatalExceptionHandler();


    /**
     * Construct a batch consumer that will automatically track the progress by updating its sequence when
     * the {@link BatchEntryHandler#onAvailable(Entry)} method returns.
     *
     * @param consumerBarrier on which it is waiting.
     * @param entryHandler is the delegate to which {@link Entry}s are dispatched.
     */
    public BatchEntryConsumer(final ConsumerBarrier<T> consumerBarrier,
                              final BatchEntryHandler<T> entryHandler)
    {
        this.consumerBarrier = consumerBarrier;
        this.entryHandler = entryHandler;
        this.noSequenceTracker = true;
    }

    /**
     * Construct a batch consumer that will rely on the {@link SequenceTrackingEntryHandler}
     * to callback via the {@link com.lmax.disruptor.BatchEntryConsumer.SequenceTrackerCallback} when it has completed with a sequence.
     *
     * @param consumerBarrier on which it is waiting.
     * @param entryHandler is the delegate to which {@link Entry}s are dispatched.
     */
    public BatchEntryConsumer(final ConsumerBarrier<T> consumerBarrier,
                              final SequenceTrackingEntryHandler<T> entryHandler)
    {
        this.consumerBarrier = consumerBarrier;
        this.entryHandler = entryHandler;

        this.noSequenceTracker = false;
        entryHandler.setSequenceTrackerCallback(new SequenceTrackerCallback());
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link BatchEntryConsumer}
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

    @Override
    public long getSequence()
    {
        return sequence;
    }

    @Override
    public ConsumerBarrier<? extends T> getConsumerBarrier()
    {
        return consumerBarrier;
    }

    @Override
    public void halt()
    {
        running = false;
        consumerBarrier.alert();
    }

    @Override
    public void run()
    {
        T entry = null;

        while (running)
        {
            try
            {
                final long nextSequence = sequence + 1;
                final long availableSeq = consumerBarrier.waitFor(nextSequence);

                for (long i = nextSequence; i <= availableSeq; i++)
                {
                    entry = consumerBarrier.getEntry(i);
                    entryHandler.onAvailable(entry);

                    if (noSequenceTracker)
                    {
                        sequence = entry.getSequence();
                    }
                }

                entryHandler.onEndOfBatch();
            }
            catch (final AlertException ex)
            {
                // Wake up from blocking wait and check if we should continue to run
            }
            catch (final Exception ex)
            {
                exceptionHandler.handle(ex, entry);
                if (noSequenceTracker)
                {
                    sequence = entry.getSequence();
                }
            }
        }

        entryHandler.onCompletion();
    }

    /**
     * Used by the {@link BatchEntryHandler} to signal when it has completed consuming a given sequence.
     */
    public final class SequenceTrackerCallback
    {
        /**
         * Notify that the handler has consumed up to a given sequence.
         *
         * @param sequence that has been consumed.
         */
        public void onCompleted(final long sequence)
        {
            BatchEntryConsumer.this.sequence = sequence;
        }
    }
}