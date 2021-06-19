package com.lmax.disruptor.dsl;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.ExceptionHandlers;

/**
 * A mutable exception handler wrapper
 * @param <T> The data type of the underlying {@link com.lmax.disruptor.RingBuffer}
 */
public class ExceptionHandlerWrapper<T> implements ExceptionHandler<T>
{
    private ExceptionHandler<? super T> delegate;

    /**
     * Switch to a different exception handler
     * @param exceptionHandler the exception handler to use from now on
     */
    public void switchTo(final ExceptionHandler<? super T> exceptionHandler)
    {
        this.delegate = exceptionHandler;
    }

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final T event)
    {
        getExceptionHandler().handleEventException(ex, sequence, event);
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        getExceptionHandler().handleOnStartException(ex);
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        getExceptionHandler() .handleOnShutdownException(ex);
    }

    private ExceptionHandler<? super T> getExceptionHandler()
    {
        ExceptionHandler<? super T> handler = delegate;
        return handler == null ? ExceptionHandlers.defaultHandler() : handler;
    }
}
