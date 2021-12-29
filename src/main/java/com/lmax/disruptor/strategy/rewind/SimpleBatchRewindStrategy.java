package com.lmax.disruptor.strategy.rewind;

import com.lmax.disruptor.strategy.RewindAction;
import com.lmax.disruptor.exception.RewindableException;

/**
 * Batch rewind strategy that always rewinds
 */
public class SimpleBatchRewindStrategy implements BatchRewindStrategy
{
    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        return RewindAction.REWIND;
    }
}
