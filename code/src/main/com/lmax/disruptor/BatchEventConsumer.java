package com.lmax.disruptor;

public final class BatchEventConsumer<T extends Entry>
    implements EventConsumer
{
    private volatile long sequence = -1L;
    private volatile boolean running = true;

    private final ThresholdBarrier<T> barrier;
    private final BatchEventHandler<T> handlerBatch;
    private EventExceptionHandler eventExceptionHandler = new FatalEventExceptionHandler();

    private final boolean noProgressTracker;

    public BatchEventConsumer(final ThresholdBarrier<T> barrier,
                              final BatchEventHandler<T> handlerBatch)
    {
        this.barrier = barrier;
        this.handlerBatch = handlerBatch;
        this.noProgressTracker = true;
    }

    public BatchEventConsumer(final ThresholdBarrier<T> barrier,
                              final ProgressReportingEventHandler<T> handler)
    {
        this.barrier = barrier;
        this.handlerBatch = handler;

        this.noProgressTracker = false;
        handler.setProgressTracker(new ProgressTrackerCallback());
    }

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

    public ThresholdBarrier getBarrier()
    {
        return barrier;
    }

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
                    entry = barrier.getRingBuffer().get(i);
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

    public final class ProgressTrackerCallback
    {
        public void onCompleted(final long sequence)
        {
            BatchEventConsumer.this.sequence = sequence;
        }
    }
}