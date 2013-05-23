package com.lmax.disruptor;

@SuppressWarnings("serial")
public final class TimeoutException extends Exception
{
    public static final TimeoutException INSTANCE = new TimeoutException();

    private TimeoutException()
    {
        // Singleton
    }

    @Override
    public synchronized Throwable fillInStackTrace()
    {
        return this;
    }
}
