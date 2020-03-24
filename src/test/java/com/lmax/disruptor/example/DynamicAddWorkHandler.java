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
package com.lmax.disruptor.example;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamicAddWorkHandler
{
    private static class MessageProduce implements Runnable
    {
        Disruptor<StubEvent> disruptor;
        int start;
        int over;
        MessageProduce(Disruptor<StubEvent> disruptor, int start, int over)
        {
            this.disruptor = disruptor;
            this.start = start;
            this.over = over;
        }

        @Override
        public void run()
        {
            for (int i = start; i < over + start; i++)
            {
                RingBuffer<StubEvent> ringBuffer = disruptor.getRingBuffer();
                long sequence = ringBuffer.next();
                try
                {
                    StubEvent event = ringBuffer.get(sequence);
                    event.setTestString("msg => " + i);
                }
                finally
                {
                    ringBuffer.publish(sequence);
                }
            }
        }
    }

    private static class DynamicHandler implements WorkHandler<StubEvent>, LifecycleAware
    {
        private final CountDownLatch shutdownLatch = new CountDownLatch(1);


        @Override
        public void onStart()
        {

        }

        @Override
        public void onShutdown()
        {
            shutdownLatch.countDown();
        }

        void awaitShutdown() throws InterruptedException
        {
            shutdownLatch.await();
        }

        @Override
        public void onEvent(StubEvent event) throws Exception
        {
            System.out.println(event.getTestString() + " ,thread ==> " +  Thread.currentThread().getId());
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<>(
                StubEvent.EVENT_FACTORY, 1024, DaemonThreadFactory.INSTANCE);
        RingBuffer<StubEvent> ringBuffer = disruptor.start();

        SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        // Construct 2 batch event processors.
        DynamicAddWorkHandler.DynamicHandler handler1 = new DynamicAddWorkHandler.DynamicHandler();
        WorkProcessor<StubEvent> processor1 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler1, new FatalExceptionHandler(), workSequence);

        DynamicAddWorkHandler.DynamicHandler handler2 = new DynamicAddWorkHandler.DynamicHandler();
        WorkProcessor<StubEvent> processor2 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler2, new FatalExceptionHandler(), workSequence);

        // Dynamically add both sequences to the ring buffer
        ringBuffer.addGatingSequences(processor1.getSequence());
        // Start the new batch processors.
        executor.execute(processor1);

        ringBuffer.addGatingSequences(processor2.getSequence());
        executor.execute(processor2);

        Thread thread1 = new Thread(new MessageProduce(disruptor, 0, 100));
        thread1.start();

        Thread.sleep(1000);

        // Remove a processor.
        // Stop the processor , processor2.haltLater() will wait for processor2 message processing to complete
        processor2.haltLater();

        Thread thread2 = new Thread(new MessageProduce(disruptor, 100, 200));
        thread2.start();

        // Wait for shutdown the complete
        handler2.awaitShutdown();
        // Remove the gating sequence from the ring buffer
        ringBuffer.removeGatingSequence(processor2.getSequence());
    }
}
