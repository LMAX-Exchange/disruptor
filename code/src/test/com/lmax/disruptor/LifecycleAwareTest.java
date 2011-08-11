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
package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEvent;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public final class LifecycleAwareTest
{
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);


    private final RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 16);
    private final DependencyBarrier dependencyBarrier = ringBuffer.newDependencyBarrier();
    private final LifecycleAwareBatchEventHandler handler = new LifecycleAwareBatchEventHandler();
    private final BatchEventProcessor batchEventProcessor = new BatchEventProcessor<StubEvent>(ringBuffer, dependencyBarrier, handler);

    @Test
    public void shouldNotifyOfBatchProcessorLifecycle() throws Exception
    {
        new Thread(batchEventProcessor).start();

        startLatch.await();
        batchEventProcessor.halt();

        shutdownLatch.await();

        assertThat(Integer.valueOf(handler.startCounter), is(Integer.valueOf(1)));
        assertThat(Integer.valueOf(handler.shutdownCounter), is(Integer.valueOf(1)));
    }

    private final class LifecycleAwareBatchEventHandler implements BatchEventHandler<StubEvent>, LifecycleAware
    {
        private int startCounter = 0;
        private int shutdownCounter = 0;

        @Override
        public void onAvailable(final StubEvent event) throws Exception
        {
        }

        @Override
        public void onEndOfBatch() throws Exception
        {
        }

        @Override
        public void onStart()
        {
            ++startCounter;
            startLatch.countDown();
        }

        @Override
        public void onShutdown()
        {
            ++shutdownCounter;
            shutdownLatch.countDown();
        }
    }
}
