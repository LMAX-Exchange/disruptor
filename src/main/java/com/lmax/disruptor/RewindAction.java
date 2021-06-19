package com.lmax.disruptor;

/**
 * The result returned from the {@link BatchRewindStrategy} that decides whether to rewind or throw the exception
 */
public enum RewindAction
{
    /**
     * Rewind and replay the whole batch from  he beginning
     */
    REWIND,

    /**
     * rethrows the exception, delegating it to the configured {@link ExceptionHandler}
     */
    THROW
}
