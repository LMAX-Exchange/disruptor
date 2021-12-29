package com.lmax.disruptor;

import com.lmax.disruptor.processor.BatchEventProcessor;

/**
 * Called by {@link BatchEventProcessor} prior to processing a batch of events.
 */
public interface BatchStartAware
{
    /**
     * Invoked by {@link BatchEventProcessor} prior to processing a batch of events
     * @param batchSize the size of the batch that is starting
     */
    void onBatchStart(long batchSize);
}
