package com.lmax.disruptor.dsl;

import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.ExceptionHandlers;

public class ExceptionHandlerWrapper<T> implements ExceptionHandler<T>
{
    private ExceptionHandler<? super T> delegate;

    public void switchTo(final ExceptionHandler<? super T> exceptionHandler)
    {
        this.delegate = exceptionHandler;
    }

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final T event)
    {
        try
        {
            getExceptionHandler().handleEventException(ex, sequence, event);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void handleOnStartException(final Throwable ex)
    {
        try
        {
            getExceptionHandler().handleOnStartException(ex);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void handleOnShutdownException(final Throwable ex)
    {
        try
        {
            getExceptionHandler().handleOnShutdownException(ex);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private ExceptionHandler<? super T> getExceptionHandler()
    {
        ExceptionHandler<? super T> handler = delegate;
        return handler == null ? ExceptionHandlers.defaultHandler() : handler;
    }
}
