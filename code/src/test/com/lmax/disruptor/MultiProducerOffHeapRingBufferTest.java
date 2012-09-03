package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Test;


public class MultiProducerOffHeapRingBufferTest
{
    @Test
    public void shouldInsertSingleMessage() throws Exception
    {
        String expected = "This is some test data";
        MultiProducerOffHeapRingBuffer buffer = MultiProducerOffHeapRingBuffer.newInstance(32, 256, new BlockingWaitStrategy());
        buffer.setGatingSequences();
        
        byte[] expectedData = expected.getBytes("ASCII");
        
        buffer.put(expectedData, 0, expectedData.length);
        
        assertThat(buffer.getEntrySize(0), is(expectedData.length));
        
        byte[] read = new byte[expectedData.length];
        buffer.getBody(0, read, 0, read.length);
        
        assertThat(new String(read), is(expected));
    }
    
    @Test
    public void shouldInsertMesssgeThatSpansMultipleBlocks() throws Exception
    {
        String expected = 
                "This is some test data, that is long than the buffer size of 256 characters so that this implementation will have " +
                "to split the data across multiple chunks in the same ring buffer. abcdefghijklmnopqrstuvwxyz1234567890, " +
                "abcdefghijklmnopqrstuvwxyz1234567890, abcdefghijklmnopqrstuvwxyz1234567890";
        MultiProducerOffHeapRingBuffer buffer = MultiProducerOffHeapRingBuffer.newInstance(32, 256, new BlockingWaitStrategy());
        buffer.setGatingSequences();
        int bodySize = buffer.getBodySize();
        byte[] expectedData = expected.getBytes("ASCII");
        
        buffer.put(expectedData, 0, expectedData.length);
        
        assertThat(buffer.getCursor(), is(1L));
        assertThat(buffer.getEntrySize(0), is(expectedData.length));
        assertThat(buffer.getPreviousSequence(0), is(-1L));
        assertThat(buffer.getPreviousSequence(1), is(0L));
        
        byte[] read = new byte[expectedData.length];
        buffer.getBody(0, read, 0, bodySize);
        buffer.getBody(1, read, bodySize, expectedData.length - bodySize);
        
        assertThat(new String(read), is(expected));
    }
}
