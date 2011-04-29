package com.lmax.disruptor;

/**
 * Callback interface to be implemented for processing {@link Entry}s as they become available in the {@link RingBuffer}
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface BatchEntryHandler<T extends Entry>
{
    /**
     * Called when a publisher has committed an {@link Entry} to the {@link RingBuffer}
     *
     * @param entry committed to the {@link RingBuffer}
     * @throws Exception if the BatchEntryHandler would like the exception handled further up the chain.
     */
    void onAvailable(T entry) throws Exception;

    /**
     * Called after each batch of items has been have been processed before the next waitFor call on a {@link ThresholdBarrier}
     *
     * @throws Exception if the BatchEntryHandler would like the exception handled further up the chain.
     */
    void onEndOfBatch() throws Exception;

    /**
     * Called when processing of {@link Entry}s is complete for clean up.
     */
    void onCompletion();
}
