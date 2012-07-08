package com.lmax.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.caliper.Runner;
import com.google.caliper.SimpleBenchmark;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;

public class DirectVsEventTranslatorWithSingleLongBenchmark extends SimpleBenchmark
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    private final PreallocatedRingBuffer<ValueEvent> ringBuffer =
            new PreallocatedRingBuffer<ValueEvent>(ValueEvent.EVENT_FACTORY, 
                    new SingleProducerSequencer(BUFFER_SIZE, new YieldingWaitStrategy()));
    private final Sequencer sequencer = ringBuffer.getSequencer();
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final ValueEventTranslator translator = new ValueEventTranslator();
    private final BatchEventProcessor<ValueEvent> batchEventProcessor = 
            new BatchEventProcessor<ValueEvent>(ringBuffer, sequenceBarrier, handler);
    {
        ringBuffer.setGatingSequences(batchEventProcessor.getSequence());
        EXECUTOR.submit(batchEventProcessor);
        try
        {
            Thread.sleep(1000);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
    
    private static class ValueEventTranslator implements EventTranslator<ValueEvent>
    {
        long value;
        
        @Override
        public void translateTo(ValueEvent event, long sequence)
        {
            event.setValue(value);
        }
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
                ringBuffer.getPreallocated(next).setValue(i);
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
            translator.value = i;
            ringBuffer.publishEvent(translator);
        }
        
        latch.await();
    }
    
    public static void main(String[] args)
    {
        Runner.main(DirectVsEventTranslatorWithSingleLongBenchmark.class, args);
    }
}
