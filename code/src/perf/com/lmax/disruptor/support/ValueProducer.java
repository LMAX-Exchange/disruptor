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

import com.lmax.disruptor.ProducerBarrier;

import java.util.concurrent.CyclicBarrier;

public final class ValueProducer implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final ProducerBarrier<ValueEntry> producerBarrier;
    private final long iterations;

    public ValueProducer(final CyclicBarrier cyclicBarrier, final ProducerBarrier<ValueEntry> producerBarrier, final long iterations)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.producerBarrier = producerBarrier;
        this.iterations = iterations;
    }

    @Override
    public void run()
    {
        try
        {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++)
            {
                ValueEntry entry = producerBarrier.nextEntry();
                entry.setValue(i);
                producerBarrier.commit(entry);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
