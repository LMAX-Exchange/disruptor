package com.lmax.disruptor;

/**
 * Used by the {@link BatchEventConsumer} to set a callback allowing the {@link BatchEventHandler} to notify
 * when it has finished consuming an event if this happens after the onEvent() call.
 * <p>
 * Typically this would be used when the handler is performing some sort of batching operation such are writing to an IO device.
 * </p>
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface ProgressReportingEventHandler<T extends Entry>
    extends BatchEventHandler<T>
{
    /**
     * Call by the {@link BatchEventConsumer} to setup the callback.
     *
     * @param progressTrackerCallback callback on which to notify the {@link BatchEventConsumer} that the sequence has progressed.
     */
    void setProgressTracker(final BatchEventConsumer.ProgressTrackerCallback progressTrackerCallback);
}
