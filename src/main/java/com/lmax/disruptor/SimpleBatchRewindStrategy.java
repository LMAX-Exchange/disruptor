package com.lmax.disruptor;

public class SimpleBatchRewindStrategy implements BatchRewindStrategy
{
    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        return RewindAction.REWIND;
    }
}
