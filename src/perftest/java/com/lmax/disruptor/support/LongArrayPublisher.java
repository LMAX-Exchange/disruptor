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

public final class LongArrayPublisher implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final RingBuffer<long[]> ringBuffer;
    private final long iterations;
    private final long arraySize;

    public LongArrayPublisher(
        final CyclicBarrier cyclicBarrier,
        final RingBuffer<long[]> ringBuffer,
        final long iterations,
        final long arraySize)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.ringBuffer = ringBuffer;
        this.iterations = iterations;
        this.arraySize = arraySize;
    }

    @Override
    public void run()
    {
        try
        {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++)
            {
                long sequence = ringBuffer.next();
                long[] event = ringBuffer.get(sequence);
                for (int j = 0; j < arraySize; j++)
                {
                    event[j] = i + j;
                }
                ringBuffer.publish(sequence);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
