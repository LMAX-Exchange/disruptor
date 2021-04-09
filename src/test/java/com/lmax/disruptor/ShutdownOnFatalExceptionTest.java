package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ShutdownOnFatalExceptionTest
{

    private final Random random = new Random();

    private final FailingEventHandler eventHandler = new FailingEventHandler();

    private Disruptor<byte[]> disruptor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setUp()
    {
        disruptor = new Disruptor<>(
                new ByteArrayFactory(256), 1024, DaemonThreadFactory.INSTANCE, ProducerType.SINGLE,
                new BlockingWaitStrategy());
        disruptor.handleEventsWith(eventHandler);
        disruptor.setDefaultExceptionHandler(new FatalExceptionHandler());
    }

    @Test
    @Timeout(value = 1000, unit = TimeUnit.MILLISECONDS)
    public void shouldShutdownGracefulEvenWithFatalExceptionHandler()
    {
        disruptor.start();

        byte[] bytes;
        for (int i = 1; i < 10; i++)
        {
            bytes = new byte[32];
            random.nextBytes(bytes);
            disruptor.publishEvent(new ByteArrayTranslator(bytes));
        }
    }

    @AfterEach
    public void tearDown()
    {
        disruptor.shutdown();
    }

    private static class ByteArrayTranslator implements EventTranslator<byte[]>
    {

        private final byte[] bytes;

        ByteArrayTranslator(final byte[] bytes)
        {
            this.bytes = bytes;
        }

        @Override
        public void translateTo(final byte[] event, final long sequence)
        {
            System.arraycopy(bytes, 0, event, 0, bytes.length);
        }
    }

    private static class FailingEventHandler implements EventHandler<byte[]>
    {
        private int count = 0;

        @Override
        public void onEvent(final byte[] event, final long sequence, final boolean endOfBatch) throws Exception
        {
            // some logging
            count++;
            if (count == 3)
            {
                throw new IllegalStateException();
            }
        }
    }

    private static class ByteArrayFactory implements EventFactory<byte[]>
    {
        private int eventSize;

        ByteArrayFactory(final int eventSize)
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
