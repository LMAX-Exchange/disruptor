/*
 * Copyright 2012 LMAX Ltd.
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

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;

public class DirectVsEventTranslatorWithSingleLongBenchmark extends SimpleBenchmark
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final RingBuffer<ValueEvent> ringBuffer =
            createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final ValueEventTranslator translator = new ValueEventTranslator();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor =
            new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handler);
    {
        ringBuffer.addGatingSequences(batchEventProcessor.getSequence());
        executor.submit(batchEventProcessor);
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    private static class ValueEventTranslator implements EventTranslator<ValueEvent>
    {
        long value;

        @Override
        public void translateTo(ValueEvent event, long sequence)
        {
            event.setValue(value);
        }
    }

    public void timeDirect(int iterations) throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        handler.reset(latch, batchEventProcessor.getSequence().get() + iterations);

        for (int i = 0; i < iterations; i++)
        {
            long next = ringBuffer.next();
            try
            {
                ringBuffer.get(next).setValue(i);
            }
            finally
            {
                ringBuffer.publish(next);
            }
        }

        latch.await();
    }

    public void timeEventTranslator(int iterations) throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        handler.reset(latch, batchEventProcessor.getSequence().get() + iterations);

        for (int i = 0; i < iterations; i++)
        {
            translator.value = i;
            ringBuffer.publishEvent(translator);
        }

        latch.await();
    }

    public static void main(String[] args)
    {
        Runner.main(DirectVsEventTranslatorWithSingleLongBenchmark.class, args);
    }
}
