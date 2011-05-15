package com.lmax.disruptor.support;

import java.util.concurrent.BlockingQueue;

public final class FunctionQueueConsumer implements Runnable
{
    private final Function function;
    private final BlockingQueue stepOneQueue;
    private final BlockingQueue stepTwoQueue;
    private final BlockingQueue stepThreeQueue;

    private volatile boolean running;
    private volatile long sequence;
    private long stepThreeCounter;

    public FunctionQueueConsumer(final Function function,
                                 final BlockingQueue stepOneQueue,
                                 final BlockingQueue stepTwoQueue,
                                 final BlockingQueue stepThreeQueue)
    {
        this.function = function;
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
    @SuppressWarnings("unchecked")
    public void run()
    {
        running = true;
        while (running)
        {
            try
            {
                switch (function)
                {
                    case STEP_ONE:
                    {
                        long[] values = (long[])stepOneQueue.take();
                        stepTwoQueue.put(Long.valueOf(values[0] + values[1]));
                        break;
                    }

                    case STEP_TWO:
                    {
                        Long value = (Long)stepTwoQueue.take();
                        stepThreeQueue.put(Long.valueOf(value.longValue() + 3));
                        break;
                    }

                    case STEP_THREE:
                    {
                        Long value = (Long)stepThreeQueue.take();
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
