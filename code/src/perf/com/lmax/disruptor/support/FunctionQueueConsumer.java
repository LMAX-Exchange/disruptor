package com.lmax.disruptor.support;

import java.util.concurrent.BlockingQueue;

public final class FunctionQueueConsumer implements Runnable
{
    private final FunctionStep functionStep;
    private final BlockingQueue<long[]> stepOneQueue;
    private final BlockingQueue<Long> stepTwoQueue;
    private final BlockingQueue<Long> stepThreeQueue;

    private volatile boolean running;
    private volatile long sequence;
    private long stepThreeCounter;

    public FunctionQueueConsumer(final FunctionStep functionStep,
                                 final BlockingQueue<long[]> stepOneQueue,
                                 final BlockingQueue<Long> stepTwoQueue,
                                 final BlockingQueue<Long> stepThreeQueue)
    {
        this.functionStep = functionStep;
        this.stepOneQueue = stepOneQueue;
        this.stepTwoQueue = stepTwoQueue;
        this.stepThreeQueue = stepThreeQueue;
    }

    public long getStepThreeCounter()
    {
        return stepThreeCounter;
    }

    public void reset()
    {
        stepThreeCounter = 0L;
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
                    {
                        long[] values = stepOneQueue.take();
                        stepTwoQueue.put(Long.valueOf(values[0] + values[1]));
                        break;
                    }

                    case TWO:
                    {
                        Long value = stepTwoQueue.take();
                        stepThreeQueue.put(Long.valueOf(value.longValue() + 3));
                        break;
                    }

                    case THREE:
                    {
                        Long value = stepThreeQueue.take();
                        long testValue = value.longValue();
                        if ((testValue & 4L) == 4L)
                        {
                            ++stepThreeCounter;
                        }
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
