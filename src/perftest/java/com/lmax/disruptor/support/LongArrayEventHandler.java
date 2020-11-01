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

import com.lmax.disruptor.BatchStartAware;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.util.PaddedLong;

import java.util.concurrent.CountDownLatch;

public final class LongArrayEventHandler implements EventHandler<long[]>, BatchStartAware
{
    private final PaddedLong value = new PaddedLong();
    private final PaddedLong batchesProcessed = new PaddedLong();
    private long count;
    private CountDownLatch latch;

    public long getValue()
    {
        return value.get();
    }

    public long getBatchesProcessed()
    {
        return batchesProcessed.get();
    }

    public void reset(final CountDownLatch latch, final long expectedCount)
    {
        value.set(0L);
        this.latch = latch;
        count = expectedCount;
        batchesProcessed.set(0);
    }

    @Override
    public void onEvent(final long[] event, final long sequence, final boolean endOfBatch) throws Exception
    {
        for (int i = 0; i < event.length; i++)
        {
            value.set(value.get() + event[i]);
        }

        if (--count == 0)
        {
            latch.countDown();
        }
    }

    @Override
    public void onBatchStart(long batchSize)
    {
        batchesProcessed.increment();
    }
}
