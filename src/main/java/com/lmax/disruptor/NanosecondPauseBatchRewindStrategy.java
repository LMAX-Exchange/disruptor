package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
 */
public class NanosecondPauseBatchRewindStrategy implements BatchRewindStrategy
{

    private final long nanoSecondPauseTime;
    private final boolean rewind;

    // Constructor which retains current functionality
    public NanosecondPauseBatchRewindStrategy(final long nanoSecondPauseTime)
    {
        this(nanoSecondPauseTime, true);
    }

    /**
     * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
     * @param  nanoSecondPauseTime Amount of nanos to pause for when a rewindable exception is thrown.
     * @param rewind Whether to return {@link RewindAction#REWIND} or {@link RewindAction#RETRY}.
     */
    public NanosecondPauseBatchRewindStrategy(final long nanoSecondPauseTime, boolean rewind)
    {
        this.nanoSecondPauseTime = nanoSecondPauseTime;
        this.rewind = rewind;
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        LockSupport.parkNanos(nanoSecondPauseTime);
        return rewind ? RewindAction.REWIND : RewindAction.RETRY;
    }
}
