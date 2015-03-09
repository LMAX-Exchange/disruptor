package com.lmax.disruptor.example;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;

public class NamedEventHandler<T> implements EventHandler<T>, LifecycleAware
{
    private String oldName;
    private final String name;

    public NamedEventHandler(final String name)
    {
        this.name = name;
    }

    @Override
    public void onEvent(final T event, final long sequence, final boolean endOfBatch) throws Exception
    {
    }

    @Override
    public void onStart()
    {
        final Thread currentThread = Thread.currentThread();
        oldName = currentThread.getName();
        currentThread.setName(name);
    }

    @Override
    public void onShutdown()
    {
        Thread.currentThread().setName(oldName);
    }
}
