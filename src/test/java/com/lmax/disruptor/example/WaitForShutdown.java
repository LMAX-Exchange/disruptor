package com.lmax.disruptor.example;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WaitForShutdown
{
    private static volatile int value = 0;

    private static class Handler implements EventHandler<LongEvent>, LifecycleAware
    {
        private final CountDownLatch latch;

        Handler(CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onStart()
        {
        }

        @Override
        public void onShutdown()
        {
            latch.countDown();
        }

        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            value = 1;
        }
    }

    public static void main(String[] args) throws TimeoutException, InterruptedException
    {
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(
            LongEvent.FACTORY, 16, DaemonThreadFactory.INSTANCE
        );

        CountDownLatch shutdownLatch = new CountDownLatch(2);

        disruptor.handleEventsWith(new Handler(shutdownLatch)).then(new Handler(shutdownLatch));
        disruptor.start();

        long next = disruptor.getRingBuffer().next();
        disruptor.getRingBuffer().get(next).set(next);
        disruptor.getRingBuffer().publish(next);

        disruptor.shutdown(10, TimeUnit.SECONDS);

        shutdownLatch.await();

        System.out.println(value);
    }
}
