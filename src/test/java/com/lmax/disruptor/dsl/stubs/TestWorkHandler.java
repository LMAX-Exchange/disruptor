package com.lmax.disruptor.dsl.stubs;

import com.lmax.disruptor.WorkHandler;
import com.lmax.disruptor.support.TestEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public class TestWorkHandler implements WorkHandler<TestEvent>
{
    private final AtomicBoolean readyToProcessEvent = new AtomicBoolean(false);
    private volatile boolean stopped = false;

    @Override
    public void onEvent(final TestEvent event) throws Exception
    {
        waitForAndSetFlag(false);
    }

    public void processEvent()
    {
        waitForAndSetFlag(true);
    }

    public void stopWaiting()
    {
        stopped = true;
    }

    private void waitForAndSetFlag(final boolean newValue)
    {
        while (!stopped && !Thread.currentThread().isInterrupted() &&
            !readyToProcessEvent.compareAndSet(!newValue, newValue))
        {
            Thread.yield();
        }
    }
}
