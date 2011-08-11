package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

/**
 * DependencyBarrier handed out for gating processorsToTrack of the RingBuffer and dependent {@link com.lmax.disruptor.EventProcessor}(s)
 */
final class EventProcessorTrackingDependencyBarrier implements DependencyBarrier
{
    private final WaitStrategy waitStrategy;
    private final Sequence cursorSequence;
    private final Sequence[] dependentProcessorSequences;
    private volatile boolean alerted = false;

    public EventProcessorTrackingDependencyBarrier(final WaitStrategy waitStrategy,
                                                   final Sequence cursorSequence,
                                                   final Sequence[] dependentProcessorSequences)
    {
        this.waitStrategy = waitStrategy;
        this.cursorSequence = cursorSequence;
        this.dependentProcessorSequences = dependentProcessorSequences;
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException
    {
        return waitStrategy.waitFor(dependentProcessorSequences, cursorSequence, this, sequence);
    }

    @Override
    public long waitFor(final long sequence, final long timeout, final TimeUnit units)
        throws AlertException, InterruptedException
    {
        return waitStrategy.waitFor(dependentProcessorSequences, cursorSequence, this, sequence, timeout, units);
    }

    @Override
    public long getCursor()
    {
        return cursorSequence.get();
    }

    @Override
    public boolean isAlerted()
    {
        return alerted;
    }

    @Override
    public void alert()
    {
        alerted = true;
        waitStrategy.signalAll();
    }

    @Override
    public void clearAlert()
    {
        alerted = false;
    }
}