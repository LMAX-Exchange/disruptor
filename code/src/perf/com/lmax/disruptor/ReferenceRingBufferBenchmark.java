package com.lmax.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;

public class ReferenceRingBufferBenchmark extends SimpleBenchmark
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final ReferenceRingBuffer<ValueEvent> ringBuffer =
            new ReferenceRingBuffer<ValueEvent>(new SingleProducerSequencer(BUFFER_SIZE, new YieldingWaitStrategy()));
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final ValueEvent event = new ValueEvent();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor = 
            new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handler);
    {
        ringBuffer.setGatingSequences(batchEventProcessor.getSequence());
        EXECUTOR.submit(batchEventProcessor);
    }
    
    @Override
    protected void setUp() throws Exception
    {
        System.out.println("foo");
    }
    
    public void timeDirect(int iterations) throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        handler.reset(latch, batchEventProcessor.getSequence().get() + iterations);
        
        for (int i = 0; i < iterations; i++)
        {
            ringBuffer.put(event);
        }
        
        latch.await();
    }
        
    public static void main(String[] args)
    {
        Runner.main(ReferenceRingBufferBenchmark.class, args);
    }
}
