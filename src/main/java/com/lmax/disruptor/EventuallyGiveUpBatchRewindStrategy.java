package com.lmax.disruptor;

public class EventuallyGiveUpBatchRewindStrategy implements BatchRewindStrategy
{
    private final long maxAttempts;

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
