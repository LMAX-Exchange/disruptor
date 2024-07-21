package com.lmax.disruptor;

import java.util.concurrent.locks.LockSupport;

/**
 * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
 *
 * <p>处理一个可回退异常的策略，将暂停指定的纳秒数。</p>
 */
public class NanosecondPauseBatchRewindStrategy implements BatchRewindStrategy
{

    private final long nanoSecondPauseTime;

    /**
     * <p>Strategy for handling a rewindableException that will pause for a specified amount of nanos.</p>
     *
     * <p>处理一个可回退异常的策略，将暂停指定的纳秒数。</p>
     *
     * @param  nanoSecondPauseTime Amount of nanos to pause for when a rewindable exception is thrown
     */
    public NanosecondPauseBatchRewindStrategy(final long nanoSecondPauseTime)
    {
        this.nanoSecondPauseTime = nanoSecondPauseTime;
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        // 暂停指定的纳秒数
        LockSupport.parkNanos(nanoSecondPauseTime);
        return RewindAction.REWIND;
    }
}
