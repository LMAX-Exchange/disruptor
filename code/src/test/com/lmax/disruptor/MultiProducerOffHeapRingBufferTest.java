package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;


public class MultiProducerOffHeapRingBufferTest
{
    @Test
    public void shouldInsertSingleMessage() throws Exception
    {
        MultiProducerOffHeapRingBuffer buffer = MultiProducerOffHeapRingBuffer.newInstance(32, 256, new BlockingWaitStrategy());
        buffer.setGatingSequences();
        
        byte[] data = "This is some test data".getBytes("ASCII");
        
        buffer.put(data, 0, data.length);
        
        assertThat(buffer.getEntrySize(0), is(data.length));
        byte[] read = new byte[data.length];
        buffer.getData(0, read, 0, read.length);
    }
}
