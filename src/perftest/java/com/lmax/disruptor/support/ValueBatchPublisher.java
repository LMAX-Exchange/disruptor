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

import java.util.concurrent.CyclicBarrier;

import com.lmax.disruptor.RingBuffer;

public final class ValueBatchPublisher implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final RingBuffer<ValueEvent> ringBuffer;
    private final long iterations;
    private final int batchSize;

    public ValueBatchPublisher(
        final CyclicBarrier cyclicBarrier,
        final RingBuffer<ValueEvent> ringBuffer,
        final long iterations,
        final int batchSize)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.ringBuffer = ringBuffer;
        this.iterations = iterations;
        this.batchSize = batchSize;
    }

    @Override
    public void run()
    {
        try
        {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i += batchSize)
            {
                long hi = ringBuffer.next(batchSize);
                long lo = hi - (batchSize - 1);
                for (long l = lo; l <= hi; l++)
                {
                    ValueEvent event = ringBuffer.get(l);
                    event.setValue(l);
                }
                ringBuffer.publish(lo, hi);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
