package com.lmax.disruptor;

/**
 * Called by the {@link BatchEventProcessor} on start of processing a batch of events.
 */
public interface BatchStartAware
{
    void onBatchStart(long batchSize);
}
