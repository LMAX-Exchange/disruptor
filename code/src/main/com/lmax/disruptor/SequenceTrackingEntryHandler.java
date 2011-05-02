package com.lmax.disruptor;

/**
 * Used by the {@link BatchEntryConsumer} to set a callback allowing the {@link BatchEntryHandler} to notify
 * when it has finished consuming an {@link Entry} if this happens after the {@link BatchEntryHandler#onAvailable(Entry)} call.
 * <p>
 * Typically this would be used when the handler is performing some sort of batching operation such are writing to an IO device.
 * </p>
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface SequenceTrackingEntryHandler<T extends Entry>
    extends BatchEntryHandler<T>
{
    /**
     * Call by the {@link BatchEntryConsumer} to setup the callback.
     *
     * @param sequenceTrackerCallback callback on which to notify the {@link BatchEntryConsumer} that the sequence has progressed.
     */
    void setSequenceTrackerCallback(final BatchEntryConsumer.SequenceTrackerCallback sequenceTrackerCallback);
}
