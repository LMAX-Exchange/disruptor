package com.lmax.disruptor;

/**
 * Callback handler for uncaught exceptions in the {@link Entry} processing cycle of the {@link BatchConsumer}
 */
public interface ExceptionHandler
{
    /**
     * Strategy for handling uncaught exceptions when processing an {@link Entry}.
     *
     * If the strategy wishes to suspend further processing by the {@link BatchConsumer}
     * then is should throw a {@link RuntimeException}.
     *
     * @param ex the exception that propagated from the {@link BatchHandler}
     * @param currentEntry being processed when the exception occurred.
     */
    void handle(Exception ex, Entry currentEntry);
}
