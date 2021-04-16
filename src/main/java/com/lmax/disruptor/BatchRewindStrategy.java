package com.lmax.disruptor;

/**
 * <p>Strategy for handling a rewindableException when processing an event.</p>
 *
 */

public interface BatchRewindStrategy
{

    /**
     * <p>When a {@link RewindableException} is thrown, this will be called.</p>
     *
     * @param e       the exception that propagated from the {@link EventHandler}.
     * @param attempts how many attempts there have been for the batch
     * @return the decision of whether to rewind the batch or throw the exception
     */
    RewindAction handleRewindException(RewindableException e, int attempts);
}
