package com.lmax.disruptor;

/**
 * Callback handler for uncaught exceptions in the {@link Entry} event processing cycle of the {@link BatchEventConsumer}
 */
public interface EventExceptionHandler
{
    /**
     * Strategy for handling uncaught exceptions when processing an event.
     *
     * If the strategy wishes to suspend further processing by the {@link BatchEventConsumer}
     * then is should throw a {@link RuntimeException}.
     *
     * @param ex the exception that propagated from the {@link BatchEventHandler}
     * @param currentEntry being processed when the exception occurred.
     */
    void handle(Exception ex, Entry currentEntry);
}
