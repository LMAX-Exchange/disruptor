/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor.support;

import java.util.concurrent.BlockingQueue;

public final class FunctionQueueProcessor implements Runnable
{
    private final FunctionStep functionStep;
    private final BlockingQueue<long[]> stepOneQueue;
    private final BlockingQueue<Long> stepTwoQueue;
    private final BlockingQueue<Long> stepThreeQueue;

    private volatile boolean running;
    private volatile long sequence;
    private long stepThreeCounter;

    public FunctionQueueProcessor(final FunctionStep functionStep,
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
