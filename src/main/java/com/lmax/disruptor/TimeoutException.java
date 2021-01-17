package com.lmax.disruptor;

@SuppressWarnings({"serial", "lgtm[java/non-sync-override]"})
public final class TimeoutException extends Exception
{
    public static final TimeoutException INSTANCE = new TimeoutException();

    private TimeoutException()
    {
        // Singleton
    }

    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
