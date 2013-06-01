package com.lmax.disruptor;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class ShutdownOnFatalExceptionTest
{

    private final Random random = new Random();

    private final FailingEventHandler eventHandler = new FailingEventHandler();

    private Disruptor<byte[]> disruptor;

    @SuppressWarnings("unchecked")
    @Before
    public void setUp()
    {
        disruptor = new Disruptor<byte[]>(new ByteArrayFactory(256), 1024, Executors.newCachedThreadPool(), ProducerType.SINGLE, new BlockingWaitStrategy());
        disruptor.handleEventsWith(eventHandler);
        disruptor.handleExceptionsWith(new FatalExceptionHandler());
    }

    @Test(timeout = 1000000)
    public void shouldShutdownGracefulEvenWithFatalExceptionHandler() throws InterruptedException, TimeoutException, AlertException
    {
        disruptor.start();

        byte[] bytes;
        for (int i = 0; i < 3; i++)
        {
            bytes = new byte[32];
            random.nextBytes(bytes);
            disruptor.publishEvent(new ByteArrayTranslator(bytes));
        }

        try
        {
            disruptor.getBarrierFor(eventHandler).waitFor(2);
        }
        finally
        {
            Thread.sleep(100); //TODO something more deterministic
            assertThat(eventHandler.shutdown.get(), is(true));
        }
    }

    @After
    public void tearDown() throws TimeoutException
    {
        disruptor.shutdown(1, TimeUnit.SECONDS);
    }

    private static class ByteArrayTranslator implements EventTranslator<byte[]>
    {

        private final byte[] bytes;

        public ByteArrayTranslator(byte[] bytes)
        {
            this.bytes = bytes;
        }

        @Override
        public void translateTo(byte[] event, long sequence)
        {
            System.arraycopy(bytes, 0, event, 0, bytes.length);
        }
    }

    private static class FailingEventHandler implements EventHandler<byte[]>, LifecycleAware
    {
        private int count = 0;
        private AtomicBoolean shutdown = new AtomicBoolean(false);

        @Override
        public void onEvent(byte[] event, long sequence, boolean endOfBatch) throws Exception
        {
            // some logging
            count++;
            if (count == 3)
            {
                throw new IllegalStateException();
            }
        }

        @Override
        public void onStart()
        {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void onShutdown()
        {
            shutdown.set(true);
        }
    }

    private static class ByteArrayFactory implements EventFactory<byte[]>
    {
        private int eventSize;

        ByteArrayFactory(int eventSize)
        {
            this.eventSize = eventSize;
        }

        @Override
        public byte[] newInstance()
        {
            return new byte[eventSize];
        }
    }
}
