package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Test;

import com.lmax.disruptor.util.DaemonThreadFactory;


public class WorkerPoolTest
{
    @SuppressWarnings("unchecked")
    @Test
    public void shouldProcessEachMessageByOnlyOneWorker() throws Exception
    {
        Executor executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        WorkerPool<AtomicLong> pool = new WorkerPool<AtomicLong>(
            new AtomicLongEventFactory(), new FatalExceptionHandler(),
            new AtomicLongWorkHandler(), new AtomicLongWorkHandler());

        RingBuffer<AtomicLong> ringBuffer = pool.start(executor);

        ringBuffer.next();
        ringBuffer.next();
        ringBuffer.publish(0);
        ringBuffer.publish(1);

        Thread.sleep(500);

        assertThat(ringBuffer.get(0).get(), is(1L));
        assertThat(ringBuffer.get(1).get(), is(1L));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldProcessOnlyOnceItHasBeenPublished() throws Exception
    {
        Executor executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        WorkerPool<AtomicLong> pool = new WorkerPool<AtomicLong>(
            new AtomicLongEventFactory(), new FatalExceptionHandler(),
            new AtomicLongWorkHandler(), new AtomicLongWorkHandler());

        RingBuffer<AtomicLong> ringBuffer = pool.start(executor);

        ringBuffer.next();
        ringBuffer.next();

        Thread.sleep(1000);

        assertThat(ringBuffer.get(0).get(), is(0L));
        assertThat(ringBuffer.get(1).get(), is(0L));
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
