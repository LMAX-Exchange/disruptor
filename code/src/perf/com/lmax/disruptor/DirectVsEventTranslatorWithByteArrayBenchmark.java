package com.lmax.disruptor;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;

public class DirectVsEventTranslatorWithByteArrayBenchmark extends SimpleBenchmark
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final PreallocatedRingBuffer<byte[]> ringBuffer =
            new PreallocatedRingBuffer<byte[]>(new ByteArrayFactory(), 
                    new SingleProducerSequencer(BUFFER_SIZE, new YieldingWaitStrategy()));
    private final Sequencer sequencer = ringBuffer.getSequencer();
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ByteArrayEventHandler handler = new ByteArrayEventHandler();
    private final ByteArrayEventTranslator translator = new ByteArrayEventTranslator();
    private final BatchEventProcessor<byte[]> batchEventProcessor = 
            new BatchEventProcessor<byte[]>(ringBuffer, sequenceBarrier, handler);
    private final byte[] data = new byte[128];
    {
        ringBuffer.setGatingSequences(batchEventProcessor.getSequence());
        EXECUTOR.submit(batchEventProcessor);
        Arrays.fill(data, (byte) 'a');
    }
    
    public void timeDirect(int iterations) throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        handler.reset(latch, batchEventProcessor.getSequence().get() + iterations);
        
        for (int i = 0; i < iterations; i++)
        {
            long next = sequencer.next();
            try
            {
                System.arraycopy(data, 0, ringBuffer.getPreallocated(next), 0, data.length);
            }
            finally
            {
                sequencer.publish(next);
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
            ringBuffer.publishEvent(translator, data);
        }
        
        latch.await();
    }
    
    private static class ByteArrayFactory implements EventFactory<byte[]>
    {
        @Override
        public byte[] newInstance()
        {
            return new byte[128];
        }
    }
    
    private static class ByteArrayEventHandler implements EventHandler<byte[]>
    {
        private long count;
        private CountDownLatch latch;
        
        public void reset(final CountDownLatch latch, final long expectedCount)
        {
            this.latch = latch;
            count = expectedCount;
        }

        @Override
        public void onEvent(byte[] event, long sequence, boolean endOfBatch) throws Exception
        {
            if (count == sequence)
            {
                latch.countDown();
            }
        }
    }
    
    private static class ByteArrayEventTranslator implements EventTranslatorOneArg<byte[], byte[]>
    {
        @Override
        public void translateTo(byte[] event, long sequence, byte[] arg0)
        {
            System.arraycopy(arg0, 0, event, 0, arg0.length);
        }
    }
    
    public static void main(String[] args)
    {
        Runner.main(DirectVsEventTranslatorWithByteArrayBenchmark.class, args);
    }
}
