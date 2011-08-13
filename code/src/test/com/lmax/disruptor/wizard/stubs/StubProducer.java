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
package com.lmax.disruptor.wizard.stubs;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.support.TestEvent;

public class StubProducer implements Runnable
{

    private volatile boolean running = true;
    private volatile int productionCount = 0;

    private final RingBuffer<TestEvent> producerBarrier;

    public StubProducer(RingBuffer<TestEvent> producerBarrier)
    {
        this.producerBarrier = producerBarrier;
    }

    public void run()
    {
        while (running)
        {
            final TestEvent entry = producerBarrier.nextEvent();
            producerBarrier.publish(entry);
            productionCount++;
        }
    }

    public int getProductionCount()
    {
        return productionCount;
    }

    public void halt()
    {
        running = false;
    }
}
