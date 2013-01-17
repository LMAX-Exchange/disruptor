package com.lmax.disruptor;

import static java.lang.String.format;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

import org.junit.Test;


public class ExpandableMemoryTest
{

    @Test
    public void shouldAllowWritesBeyondInitialBufferSize() throws Exception
    {
        File f = File.createTempFile("mike", ".map");
        f.deleteOnExit();
        RandomAccessFile raf = new RandomAccessFile(f, "rw");
        FileChannel channel = raf.getChannel();
        
        int entryCount = 1024;
        ExpandableMemory memory = new ExpandableMemory(channel, entryCount, 16);
        long fudge01 = 47;
        long fudge02 = 53;
        
        for (int i = 0; i < entryCount * 2; i++)
        {
            long reference = memory.referenceFor(i, true);
            
            memory.putLong(reference, 0, i + fudge01);
            assertThat(format("i = %d, reference = %d", i, reference), memory.getLong(reference, 0), is(i + fudge01));
            memory.putLong(reference, 8, i + fudge02);            
            assertThat(format("i = %d, reference = %d", i, reference), memory.getLong(reference, 8), is(i + fudge02));
        }
        
        for (int i = 0; i < entryCount * 2; i++)
        {
            long reference = memory.referenceFor(i);
            
            assertThat(format("i = %d, reference = %d", i, reference), memory.getLong(reference, 0), is(i + fudge01));
            assertThat(format("i = %d, reference = %d", i, reference), memory.getLong(reference, 8), is(i + fudge02));
        }
    }
}
