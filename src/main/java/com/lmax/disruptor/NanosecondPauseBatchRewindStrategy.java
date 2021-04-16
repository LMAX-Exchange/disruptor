package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
 */
public class NanosecondPauseBatchRewindStrategy implements BatchRewindStrategy
{

    private final long nanoSecondPauseTime;

    /**
     * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
     * @param  nanoSecondPauseTime Amount of nanos to pause for when a rewindable exception is thrown
     */
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
