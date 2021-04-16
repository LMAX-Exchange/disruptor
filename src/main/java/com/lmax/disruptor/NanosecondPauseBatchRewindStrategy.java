package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

public class NanosecondPauseBatchRewindStrategy implements BatchRewindStrategy
{

    private final long nanoSecondPauseTime;

    public NanosecondPauseBatchRewindStrategy(final long nanoSecondPauseTime)
    {
        this.nanoSecondPauseTime = nanoSecondPauseTime;
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        LockSupport.parkNanos(nanoSecondPauseTime);
        return RewindAction.REWIND;
    }
}
