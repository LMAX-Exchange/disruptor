package com.lmax.disruptor.support;

import com.lmax.disruptor.*;

public class DummyWaitStrategy implements WaitStrategy
{
    public int signalAllWhenBlockingCalls = 0;

    @Override
    public long waitFor(
        long sequence, Sequence cursor, Sequence dependentSequence, SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException
    {
        return 0;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        signalAllWhenBlockingCalls++;
    }
}
