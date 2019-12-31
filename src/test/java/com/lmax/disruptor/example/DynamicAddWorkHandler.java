package com.lmax.disruptor.example;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @Author : Rookiex
 * @Date : Created in 2019/12/31 13:05
 * @Describe :
 * @version:
 */
public class DynamicAddWorkHandler {


    private static class MessageProduce implements Runnable {
        Disruptor<StubEvent> disruptor;

        MessageProduce(Disruptor<StubEvent> disruptor) {
            this.disruptor = disruptor;
        }


        @Override
        public void run() {
            int msgCount = 10;
            for (int i = 0; i < msgCount; i++) {
                StubEvent stubEvent = StubEvent.EVENT_FACTORY.newInstance();
                stubEvent.setTestString("msg => " + i);
                final int finalI = i;
                disruptor.getRingBuffer().publishEvent((a, b, c) -> {
                    a.setTestString("msg => " + finalI);
                });
            }
        }
    }

    private static class DynamicHandler implements WorkHandler<StubEvent>, LifecycleAware {
        private final CountDownLatch shutdownLatch = new CountDownLatch(1);


        @Override
        public void onStart() {

        }

        @Override
        public void onShutdown() {
            shutdownLatch.countDown();
        }

        public void awaitShutdown() throws InterruptedException {
            shutdownLatch.await();
        }

        @Override
        public void onEvent(StubEvent event) throws Exception {
            System.out.println(event.getTestString() + " ==> " +  Thread.currentThread().getId());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<StubEvent>(
                StubEvent.EVENT_FACTORY, 1024, DaemonThreadFactory.INSTANCE);
        RingBuffer<StubEvent> ringBuffer = disruptor.start();

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

        Thread thread1 = new Thread(new MessageProduce(disruptor));
        thread1.start();

        Thread.sleep(2000);

        // Remove a processor.
        // Stop the processor , processor2.haltLater() will wait for all processor2 message processing to complete
        processor2.haltLater();

        Thread thread2 = new Thread(new MessageProduce(disruptor));
        thread2.start();

        // Wait for shutdown the complete
        handler2.awaitShutdown();
        // Remove the gating sequence from the ring buffer
        ringBuffer.removeGatingSequence(processor2.getSequence());
    }
}
