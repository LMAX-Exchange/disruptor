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

import com.lmax.disruptor.PreallocatedRingBuffer;
import com.lmax.disruptor.Sequencer;

public final class ValuePublisher implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final PreallocatedRingBuffer<ValueEvent> ringBuffer;
    private final long iterations;

    public ValuePublisher(final CyclicBarrier cyclicBarrier, final PreallocatedRingBuffer<ValueEvent> ringBuffer, final long iterations)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.ringBuffer = ringBuffer;
        this.iterations = iterations;
    }

    @Override
    public void run()
    {
        Sequencer sequencer = ringBuffer.getSequencer();
        try
        {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++)
            {
                long sequence = sequencer.next();
                ValueEvent event = ringBuffer.getPreallocated(sequence);
                event.setValue(i);
                sequencer.publish(sequence);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
