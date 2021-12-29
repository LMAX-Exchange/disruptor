package com.lmax.disruptor.support;

import com.lmax.disruptor.exception.AlertException;
import com.lmax.disruptor.sequence.Sequence;
import com.lmax.disruptor.barrier.SequenceBarrier;
import com.lmax.disruptor.exception.TimeoutException;
import com.lmax.disruptor.strategy.wait.WaitStrategy;

public class DummyWaitStrategy implements WaitStrategy
{
    public int signalAllWhenBlockingCalls = 0;

    @Override
    public long waitFor(
            final long sequence, final Sequence cursor, final Sequence dependentSequence, final SequenceBarrier barrier)
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
