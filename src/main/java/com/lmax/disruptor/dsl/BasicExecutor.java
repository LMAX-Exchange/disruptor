package com.lmax.disruptor.dsl;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

public class BasicExecutor implements Executor
{
    private final ThreadFactory factory;

    public BasicExecutor(ThreadFactory factory)
    {
        this.factory = factory;
    }

    @Override
    public void execute(Runnable command)
    {
        final Thread thread = factory.newThread(command);
        if (null == thread)
        {
            throw new RuntimeException("Failed to create thread to run: " + command);
        }

        thread.start();
    }
}
