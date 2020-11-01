package com.lmax.disruptor;

import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class WorkerPoolTest
{
    @SuppressWarnings("unchecked")
    @Test
    public void shouldProcessEachMessageByOnlyOneWorker() throws Exception
    {
        Executor executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        WorkerPool<AtomicLong> pool = new WorkerPool<>(
                new AtomicLongEventFactory(), new FatalExceptionHandler(),
                new AtomicLongWorkHandler(), new AtomicLongWorkHandler());

        RingBuffer<AtomicLong> ringBuffer = pool.start(executor);

        ringBuffer.next();
        ringBuffer.next();
        ringBuffer.publish(0);
        ringBuffer.publish(1);

        Thread.sleep(500);

        assertEquals(1L, ringBuffer.get(0).get());
        assertEquals(1L, ringBuffer.get(1).get());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldProcessOnlyOnceItHasBeenPublished() throws Exception
    {
        Executor executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        WorkerPool<AtomicLong> pool = new WorkerPool<>(
                new AtomicLongEventFactory(), new FatalExceptionHandler(),
                new AtomicLongWorkHandler(), new AtomicLongWorkHandler());

        RingBuffer<AtomicLong> ringBuffer = pool.start(executor);

        ringBuffer.next();
        ringBuffer.next();

        Thread.sleep(1000);

        assertEquals(0L, ringBuffer.get(0).get());
        assertEquals(0L, ringBuffer.get(1).get());
    }

    private static class AtomicLongWorkHandler implements WorkHandler<AtomicLong>
    {
        @Override
        public void onEvent(AtomicLong event) throws Exception
        {
            event.incrementAndGet();
        }
    }


    private static class AtomicLongEventFactory implements EventFactory<AtomicLong>
    {
        @Override
        public AtomicLong newInstance()
        {
            return new AtomicLong(0);
        }
    }
}
