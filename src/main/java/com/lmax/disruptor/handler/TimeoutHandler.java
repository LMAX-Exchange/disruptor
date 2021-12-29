package com.lmax.disruptor.handler;

import com.lmax.disruptor.exception.TimeoutException;
import com.lmax.disruptor.handler.eventhandler.EventHandler;
import com.lmax.disruptor.processor.BatchEventProcessor;
import com.lmax.disruptor.strategy.wait.WaitStrategy;

/**
 * When a {@link BatchEventProcessor} with a {@link WaitStrategy} that throws
 * {@link TimeoutException} detects that its {@link EventHandler} implements this
 * interface, it notifies that event handler whenever the wait strategy's timeout
 * is exceeded, via `onTimeout`.
 */
public interface TimeoutHandler
{
    /**
     * Invoked when a {@link BatchEventProcessor}'s {@link WaitStrategy} throws a {@link TimeoutException}.
     * @param sequence - the last processed sequence.
     * @throws Exception if the implementation is unable to handle this timeout.
     */
    void onTimeout(long sequence) throws Exception;
}
