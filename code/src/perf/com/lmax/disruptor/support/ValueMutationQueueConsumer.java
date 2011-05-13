package com.lmax.disruptor.support;

import java.util.concurrent.BlockingQueue;

public final class ValueMutationQueueConsumer implements Runnable
{
    private volatile boolean running;
    private volatile long sequence;
    private long value;

    private final BlockingQueue<Long> blockingQueue;
    private final Operation operation;

    public ValueMutationQueueConsumer(final BlockingQueue<Long> blockingQueue, final Operation operation)
    {
        this.blockingQueue = blockingQueue;
        this.operation = operation;
    }

    public long getValue()
    {
        return value;
    }

    public void reset()
    {
        value = 0L;
    }

    public long getSequence()
    {
        return sequence;
    }

    public void halt()
    {
        running = false;
    }

    @Override
    public void run()
    {
        running = true;
        while (running)
        {
            try
            {
                long value = blockingQueue.take().longValue();
                this.value = operation.op(this.value, value);
                sequence = value;
            }
            catch (InterruptedException ex)
            {
                break;
            }
        }
    }
}
