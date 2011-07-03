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
