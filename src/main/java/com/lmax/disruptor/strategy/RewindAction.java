package com.lmax.disruptor.strategy;

import com.lmax.disruptor.handler.exceptionhandler.ExceptionHandler;
import com.lmax.disruptor.strategy.rewind.BatchRewindStrategy;

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
