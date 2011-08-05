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

public final class ValueAdditionQueueProcessor implements Runnable
{
    private volatile boolean running;
    private volatile long sequence;
    private long value;

    private final BlockingQueue<Long> blockingQueue;

    public ValueAdditionQueueProcessor(final BlockingQueue<Long> blockingQueue)
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
