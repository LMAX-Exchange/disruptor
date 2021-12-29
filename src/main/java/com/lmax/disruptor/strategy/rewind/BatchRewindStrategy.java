package com.lmax.disruptor.strategy.rewind;

import com.lmax.disruptor.strategy.RewindAction;
import com.lmax.disruptor.exception.RewindableException;
import com.lmax.disruptor.handler.eventhandler.EventHandler;

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
