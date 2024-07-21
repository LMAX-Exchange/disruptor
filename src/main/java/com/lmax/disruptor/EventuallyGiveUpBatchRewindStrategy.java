package com.lmax.disruptor;

/**
 * <p>Strategy for handling a rewindableException that will eventually delegate the exception to the
 * {@link ExceptionHandler} after a specified number of attempts have been made.</p>
 *
 * <p>用于处理 RewindableException 的策略，最终将异常委托给 ExceptionHandler，委托前会尝试指定次数。</p>
 */
public class EventuallyGiveUpBatchRewindStrategy implements BatchRewindStrategy
{
    private final long maxAttempts;

    /**
     * @param maxAttempts numbers of Rewindable exceptions that can be thrown until exception is delegated
     */
    public EventuallyGiveUpBatchRewindStrategy(final long maxAttempts)
    {
        this.maxAttempts = maxAttempts;
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        if (retriesAttempted == maxAttempts)
        {
            return RewindAction.THROW;
        }
        return RewindAction.REWIND;
    }
}
