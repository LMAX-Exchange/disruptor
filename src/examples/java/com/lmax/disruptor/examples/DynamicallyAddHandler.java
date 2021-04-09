package com.lmax.disruptor.examples;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.examples.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DynamicallyAddHandler
{
    private static class DynamicHandler implements EventHandler<StubEvent>, LifecycleAware
    {
        private final CountDownLatch shutdownLatch = new CountDownLatch(1);

        @Override
        public void onEvent(final StubEvent event, final long sequence, final boolean endOfBatch)
        {
        }

        @Override
        public void onStart()
        {

        }

        @Override
        public void onShutdown()
        {
            shutdownLatch.countDown();
        }

        public void awaitShutdown() throws InterruptedException
        {
            shutdownLatch.await();
        }
    }

    public static void main(final String[] args) throws InterruptedException
    {
        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<>(
                StubEvent.EVENT_FACTORY, 1024, DaemonThreadFactory.INSTANCE);
        RingBuffer<StubEvent> ringBuffer = disruptor.start();

        // Construct 2 batch event processors.
        DynamicHandler handler1 = new DynamicHandler();
        BatchEventProcessor<StubEvent> processor1 =
                new BatchEventProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler1);

        DynamicHandler handler2 = new DynamicHandler();
        BatchEventProcessor<StubEvent> processor2 =
                new BatchEventProcessor<>(ringBuffer, ringBuffer.newBarrier(processor1.getSequence()), handler2);

        // Dynamically add both sequences to the ring buffer
        ringBuffer.addGatingSequences(processor1.getSequence(), processor2.getSequence());

        // Start the new batch processors.
        executor.execute(processor1);
        executor.execute(processor2);

        // Remove a processor.

        // Stop the processor
        processor2.halt();
        // Wait for shutdown the complete
        handler2.awaitShutdown();
        // Remove the gating sequence from the ring buffer
        ringBuffer.removeGatingSequence(processor2.getSequence());
    }
}
