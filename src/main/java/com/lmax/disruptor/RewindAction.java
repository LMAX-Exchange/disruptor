package com.lmax.disruptor;

/**
 * The result returned from the {@link BatchRewindStrategy} that decides whether to rewind, retry or throw the exception
 */
public enum RewindAction
{
    /**
     * Rewind and replay the whole batch from the beginning sequence value.
     */
    REWIND,
    /**
     * Retry the batch from the current sequence value.
     */
    RETRY,
    /**
     * Rethrows the exception, delegating it to the configured {@link ExceptionHandler}.
     */
    THROW
}
