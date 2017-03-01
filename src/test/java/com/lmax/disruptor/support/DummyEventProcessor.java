package com.lmax.disruptor.support;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SingleProducerSequencer;

import java.util.concurrent.atomic.AtomicBoolean;

public class DummyEventProcessor implements EventProcessor
{
    private final Sequence sequence;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public DummyEventProcessor(Sequence sequence)
    {
        this.sequence = sequence;
    }

    public DummyEventProcessor()
    {
        this(new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE));
    }

    public void setSequence(long sequence)
    {
        this.sequence.set(sequence);
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(false);
    }

    @Override
    public boolean isRunning()
    {
        return running.get();
    }

    @Override
    public void run()
    {
        if (!running.compareAndSet(false, true))
        {
            throw new IllegalStateException("Already running");
        }
    }
}
