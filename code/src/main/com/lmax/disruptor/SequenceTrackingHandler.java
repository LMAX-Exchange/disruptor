package com.lmax.disruptor;

/**
 * Used by the {@link BatchConsumer} to set a callback allowing the {@link BatchHandler} to notify
 * when it has finished consuming an {@link Entry} if this happens after the {@link BatchHandler#onAvailable(Entry)} call.
 * <p>
 * Typically this would be used when the handler is performing some sort of batching operation such are writing to an IO device.
 * </p>
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface SequenceTrackingHandler<T extends Entry>
    extends BatchHandler<T>
{
    /**
     * Call by the {@link BatchConsumer} to setup the callback.
     *
     * @param sequenceTrackerCallback callback on which to notify the {@link BatchConsumer} that the sequence has progressed.
     */
    void setSequenceTrackerCallback(final BatchConsumer.SequenceTrackerCallback sequenceTrackerCallback);
}
