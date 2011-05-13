package com.lmax.disruptor.support;

import java.util.concurrent.BlockingQueue;

public final class ValueAdditionQueueConsumer implements Runnable
{
    private volatile boolean running;
    private volatile long sequence;
    private long value;

    private final BlockingQueue<Long> blockingQueue;

    public ValueAdditionQueueConsumer(final BlockingQueue<Long> blockingQueue)
    {
        this.blockingQueue = blockingQueue;
    }

    public long getValue()
    {
        return value;
    }

    public void reset()
    {
        value = 0L;
        sequence = -1L;
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
                this.value += value;
                sequence++;
            }
            catch (InterruptedException ex)
            {
                break;
            }
        }
    }
}
