package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

public class NanosecondRewindPauseStrategy implements RewindPauseStrategy
{

    private final long nanoSecondPauseTime;

    public NanosecondRewindPauseStrategy(final long nanoSecondPauseTime)
    {
        this.nanoSecondPauseTime = nanoSecondPauseTime;
    }

    @Override
    public void pause()
    {
        LockSupport.parkNanos(nanoSecondPauseTime);
    }
}
