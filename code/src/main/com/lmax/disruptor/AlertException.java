package com.lmax.disruptor;

/**
 * Used to alert consumers waiting at a {@link ConsumerBarrier} of status changes.
 * <P>
 * It does not fill in a stack trace for performance reasons.
 */
@SuppressWarnings("serial")
public class AlertException extends Exception
{
    /** Pre-allocated exception to avoid garbage generation */
    public static final AlertException ALERT_EXCEPTION = new AlertException();

    /**
     * Private constructor so only a single instance exists.
     */
    private AlertException()
    {
    }

    /**
     * Overridden so the stack trace is not filled in for this exception for performance reasons.
     *
     * @return this instance.
     */
    @Override
    public Throwable fillInStackTrace()
    {
        return this;
    }
}
