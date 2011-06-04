package com.lmax.disruptor.support;

import com.lmax.disruptor.collections.Histogram;

import java.util.concurrent.BlockingQueue;

public final class LatencyStepQueueConsumer implements Runnable
{
    private final FunctionStep functionStep;

    private final BlockingQueue<Long> inputQueue;
    private final BlockingQueue<Long> outputQueue;
    private final Histogram histogram;
    private final long nanoTimeCost;

    private volatile boolean running;
    private volatile long sequence;

    public LatencyStepQueueConsumer(final FunctionStep functionStep,
                                    final BlockingQueue<Long> inputQueue,
                                    final BlockingQueue<Long> outputQueue,
                                    final Histogram histogram, final long nanoTimeCost)
    {
        this.functionStep = functionStep;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.histogram = histogram;
        this.nanoTimeCost = nanoTimeCost;
    }

    public void reset()
    {
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
                switch (functionStep)
                {
                    case ONE:
                    case TWO:
                    {
                        outputQueue.put(inputQueue.take());
                        break;
                    }

                    case THREE:
                    {
                        Long value = inputQueue.take();
                        long duration = System.nanoTime() - value.longValue();
                        duration /= 3;
                        duration -= nanoTimeCost;
                        histogram.addObservation(duration);
                        break;
                    }
                }

                sequence++;
            }
            catch (InterruptedException ex)
            {
                break;
            }
        }
    }
}
