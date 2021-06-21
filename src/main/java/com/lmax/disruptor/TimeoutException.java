package com.lmax.disruptor;

/**
 * Wait strategies may throw this Exception to inform callers that a
 * message has not been detected within a specific time window.
 * For efficiency, a single instance is provided.
 */
@SuppressWarnings({"serial", "lgtm[java/non-sync-override]"})
public final class TimeoutException extends Exception
{
    /**
     * The efficiency saving singleton instance
     */
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
