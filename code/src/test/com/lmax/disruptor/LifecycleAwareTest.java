package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public final class LifecycleAwareTest
{
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);


    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 16);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final LifecycleAwareBatchHandler handler = new LifecycleAwareBatchHandler();
    private final BatchConsumer batchConsumer = new BatchConsumer<StubEntry>(consumerBarrier, handler);

    @Test
    public void shouldNotifyOfBatchConsumerLifecycle() throws Exception
    {
        new Thread(batchConsumer).start();

        startLatch.await();
        batchConsumer.halt();

        shutdownLatch.await();

        assertThat(Integer.valueOf(handler.startCounter), is(Integer.valueOf(1)));
        assertThat(Integer.valueOf(handler.shutdownCounter), is(Integer.valueOf(1)));
    }

    private final class LifecycleAwareBatchHandler implements BatchHandler<StubEntry>, LifecycleAware
    {
        private int startCounter = 0;
        private int shutdownCounter = 0;

        @Override
        public void onAvailable(final StubEntry entry) throws Exception
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
