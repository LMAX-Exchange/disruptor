package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.lmax.disruptor.util.Bits;


public class OffHeapRingBufferTest
{
    OffHeapRingBuffer<SimpleData> ringBuffer = OffHeapRingBuffer.newMultiProducer(new BlockingWaitStrategy(), 
                                                                                  SimpleDataEntry.FACTORY, 
                                                                                  32, 256);
    
    @Test
    public void shouldPublishSingleEntry() throws Exception
    {
        byte[] data = "This is some test data".getBytes();
        
        assertInsertAndGet(ringBuffer.createProducer(), ringBuffer, data, 0);
    }
    
    @Test
    public void shouldPublishMultipleEntries() throws Exception
    {
        Producer<SimpleData> producer = ringBuffer.createProducer();
        
        for (int i = 0; i < 1000; i++)
        {
            byte[] data = ("This is some test data_" + i).getBytes();
            assertInsertAndGet(producer, ringBuffer, data, i);            
        }
    }
    
    @Test
    public void shouldNotifyHandler() throws Exception
    {
        final byte[] input = "Some more test data passed between threads".getBytes();
        final CountDownLatch latch = new CountDownLatch(1);        
        final byte[] output = new byte[input.length];
        
        EventHandler<SimpleData> handler = new EventHandler<SimpleData>()
        {
            @Override
            public void onEvent(SimpleData event, long sequence, boolean endOfBatch) throws Exception
            {
                byte[] data = event.getData();
                System.arraycopy(data, 0, output, 0, data.length);
                latch.countDown();
            }
        };
        
        BatchEventProcessor<SimpleData> processor = 
                new BatchEventProcessor<SimpleData>(ringBuffer.createDataSource(),
                                                    ringBuffer.newBarrier(),
                                                    handler);
        
        ringBuffer.addGatingSequences(processor.getSequence());
        
        Thread t = new Thread(processor);
        t.start();
        
        Producer<SimpleData> producer = ringBuffer.createProducer();
        
        SimpleData entry = producer.next();
        entry.setDataLength(input.length);
        entry.setData(input, 0, input.length);
        
        producer.publish();
        
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertTrue(Arrays.equals(input, output));
        
        processor.halt();
    }

    private void assertInsertAndGet(Producer<SimpleData> producer, OffHeapRingBuffer<SimpleData> ringBuffer, 
                                    byte[] data, long expectedSequence)
    {
        SimpleData input = producer.next();
        
        long sequence = producer.currentSequence();
        assertThat(sequence, is(expectedSequence));
        
        input.setLength(data.length + Bits.sizeofInt());
        input.setDataLength(data.length);
        input.setData(data, 0, data.length);
        assertTrue(Arrays.equals(data, input.getData()));
        
        producer.publish();
        
        SimpleData output = ringBuffer.getPublished(null, sequence);
        
        assertTrue(Arrays.equals(data, output.getData()));
    }
}
