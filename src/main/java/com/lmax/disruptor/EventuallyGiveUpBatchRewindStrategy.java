package com.lmax.disruptor;

/**
 * <p>Strategy for handling a rewindableException that will eventually delegate the exception to the
 * {@link ExceptionHandler} after a specified number of attempts have been made.</p>
 */
public class EventuallyGiveUpBatchRewindStrategy implements BatchRewindStrategy
{
    private final long maxAttempts;
    private final boolean rewind;

    // Constructor which retains current functionality
    public EventuallyGiveUpBatchRewindStrategy(final long maxAttempts)
    {
        this(maxAttempts, true);
    }

    /**
     * @param maxAttempts numbers of Rewindable exceptions that can be thrown until exception is delegated
     * @param rewind Whether to return {@link RewindAction#REWIND} or {@link RewindAction#RETRY}.
     */
    public EventuallyGiveUpBatchRewindStrategy(final long maxAttempts, final boolean rewind)
    {
        this.maxAttempts = maxAttempts;
        this.rewind = rewind;
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        if (retriesAttempted >= maxAttempts)
        {
            return RewindAction.THROW;
        }
        return rewind ? RewindAction.REWIND : RewindAction.RETRY;
    }
}
