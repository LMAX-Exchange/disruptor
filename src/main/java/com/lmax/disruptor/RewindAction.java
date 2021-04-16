package com.lmax.disruptor;

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
