package com.lmax.disruptor;

/**
 * Strategy for handling a rewindableException when processing an event.
 */
public interface BatchRewindStrategy
{

    /**
     * When a {@link RewindableException} is thrown, this will be called.
     *
     * @param e       the exception that propagated from the {@link EventHandler}.
     * @param attempts how many attempts there have been for the batch
     * @return the decision of whether to rewind the batch or throw the exception
     */
    RewindAction handleRewindException(RewindableException e, int attempts);
}
