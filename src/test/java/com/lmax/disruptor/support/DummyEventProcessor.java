package com.lmax.disruptor.support;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;

public class DummyEventProcessor implements EventProcessor
{
    private final Sequence sequence;
    private boolean isRunning;

    public DummyEventProcessor(Sequence sequence)
    {
        this.sequence = sequence;
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        isRunning = false;
    }

    @Override
    public boolean isRunning()
    {
        return isRunning;
    }

    @Override
    public void run()
    {
        isRunning = true;
    }
}
