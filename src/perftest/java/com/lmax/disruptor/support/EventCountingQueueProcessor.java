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

import com.lmax.disruptor.util.PaddedLong;

import java.util.concurrent.BlockingQueue;

public final class EventCountingQueueProcessor implements Runnable
{
    private volatile boolean running;
    private final BlockingQueue<Long> blockingQueue;
    private final PaddedLong[] counters;
    private final int index;

    public EventCountingQueueProcessor(
        final BlockingQueue<Long> blockingQueue, final PaddedLong[] counters, final int index)
    {
        this.blockingQueue = blockingQueue;
        this.counters = counters;
        this.index = index;
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
                blockingQueue.take();
                counters[index].set(counters[index].get() + 1L);
            }
            catch (InterruptedException ex)
            {
                break;
            }
        }
    }
}
