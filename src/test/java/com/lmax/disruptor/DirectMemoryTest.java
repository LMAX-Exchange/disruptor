package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2CharOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ShortOpenHashMap;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.lmax.disruptor.util.Bits;

@RunWith(Parameterized.class)
public class DirectMemoryTest
{
    private MemoryAllocator memoryAllocator;
        
    public DirectMemoryTest(MemoryAllocator memoryAllocator)
    {
        this.memoryAllocator = memoryAllocator;
    }
    
    @Parameters
    public static Collection<Object[]> generateData()
    {
        Object[][] allocators =
        {
         { DirectMemory.getByteArrayAllocator() },
         { DirectMemory.getByteBufferAllocator() },
         { getMemoryMappedAllocator() }
        };
        return Arrays.asList(allocators);
    }
    
    @Test
    public void shouldPutAndGetForLongs() throws Exception
    {
        assertPutAndGet(new LongValidator());        
    }
    
    @Test
    public void shouldPutAndGetForInts() throws Exception
    {
        assertPutAndGet(new IntValidator());        
    }
    
    @Test
    public void shouldPutAndGetForByteArrays() throws Exception
    {
        assertPutAndGet(new ByteArrayValidator());
    }
    
    @Test
    public void shouldPutAndGetForDoubles() throws Exception
    {
        assertPutAndGet(new DoubleValidator());
    }
    
    @Test
    public void shouldPutAndGetForFloats() throws Exception
    {
        assertPutAndGet(new FloatValidator());
    }
    
    @Test
    public void shouldPutAndGetForShorts() throws Exception
    {
        assertPutAndGet(new ShortValidator());
    }
    
    @Test
    public void shouldPutAndGetForChars() throws Exception
    {
        assertPutAndGet(new CharValidator());
    }    
    
    @Test
    public void shouldPutAndGetForBytes() throws Exception
    {
        assertPutAndGet(new ByteValidator());
    }    
    
    @Test
    public void shouldPutAndGetForBytes2() throws Exception
    {
        Random r = new Random(1);
        
        int size      = 1 << r.nextInt(13);
        int chunkSize = 1 << (r.nextInt(4) + 5);
        assertPutGetForSingleType(r, 1024, size, chunkSize, new ByteArrayValidator());
    }    
    
    private static class ByteArrayValidator implements TypeValidator
    {
        private static final int BYTE_ARRAY_SIZE = 16;
        
        private final    Long2ObjectOpenHashMap<byte[]> values = new Long2ObjectOpenHashMap<byte[]>();
        private byte[][] input;

        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new byte[numberOfPuts][BYTE_ARRAY_SIZE]);
        }

        @Override
        public boolean putAndGetAt(Memory memory, int i, long reference, int offset)
        {
            memory.putBytes(reference, offset, input[i], 0, input[i].length);
            values.put(reference + offset, Arrays.copyOf(input[i], input[i].length));
            byte[] stored = new byte[16];
            int amountRead = memory.getBytes(reference, offset, BYTE_ARRAY_SIZE, stored);
            
            assertThat(amountRead, is(BYTE_ARRAY_SIZE));
            
            return Arrays.equals(stored, input[i]);
        }

        @Override
        public boolean validateGetAt(Memory memory, long reference, int offset)
        {
            byte[] stored = memory.getBytes(reference, offset, BYTE_ARRAY_SIZE);
            return Arrays.equals(stored, values.get(reference + offset));
        }

        @Override
        public int getTypeSize()
        {
            return BYTE_ARRAY_SIZE;
        }        
    }
    
    private static class LongValidator implements TypeValidator
    {
        private final Long2LongOpenHashMap values = new Long2LongOpenHashMap();
        private long[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new long[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putLong(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getLong(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getLong(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofLong();
        }
    }
    
    private static class IntValidator implements TypeValidator
    {
        private final Long2IntOpenHashMap values = new Long2IntOpenHashMap();
        private int[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new int[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putInt(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getInt(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getInt(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofInt();
        }
    }
    
    private static class DoubleValidator implements TypeValidator
    {
        private final Long2DoubleOpenHashMap values = new Long2DoubleOpenHashMap();
        private double[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new double[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putDouble(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getDouble(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getDouble(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofDouble();
        }
    }
    
    private static class FloatValidator implements TypeValidator
    {
        private final Long2FloatOpenHashMap values = new Long2FloatOpenHashMap();
        private float[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new float[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putFloat(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getFloat(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getFloat(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofFloat();
        }
    }
    
    private static class ShortValidator implements TypeValidator
    {
        private final Long2ShortOpenHashMap values = new Long2ShortOpenHashMap();
        private short[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new short[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putShort(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getShort(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getShort(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofShort();
        }
    }
    
    private static class CharValidator implements TypeValidator
    {
        private final Long2CharOpenHashMap values = new Long2CharOpenHashMap();
        private char[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new char[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putChar(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getChar(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getChar(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofChar();
        }
    }
    
    private static class ByteValidator implements TypeValidator
    {
        private final Long2ByteOpenHashMap values = new Long2ByteOpenHashMap();
        private byte[] input;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new byte[numberOfPuts]);
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, long index, int offset)
        {
            memory.putByte(index, offset, input[i]);
            values.put(index + offset, input[i]);
            
            return memory.getByte(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, long index, int offset)
        {
            return memory.getByte(index, offset) == values.get(index + offset);
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofByte();
        }
    }

    private void assertPutAndGet(TypeValidator validator)
    {
        Random r = new Random();
        
        for (int i = 0; i < 20; i++)
        {
            int size      = 1 << r.nextInt(13);
            int chunkSize = 1 << (r.nextInt(4) + 5);
            assertPutGetForSingleType(r, 1024, size, chunkSize, validator);
        }
    }

    private void assertPutGetForSingleType(Random random, 
                                           int numberOfPuts, 
                                           int size, 
                                           int chunkSize, 
                                           TypeValidator validator)
    {
        Memory memory = memoryAllocator.allocate(size, chunkSize);
        
        int    typeSize   = validator.getTypeSize();
        int    alignment  = random.nextInt(typeSize);
        long[] references = new long[numberOfPuts];
        int[]  offsets    = new int[numberOfPuts];
        
        validator.init(random, numberOfPuts, size, chunkSize);
        
        for (int i = 0; i < numberOfPuts; i++)
        {
            long reference = memory.indexOf(random.nextInt(memory.getEntryCount()));
            int offset = randomOffset(random, memory, typeSize, alignment);
            
            assertTrue("value " + i + " is incorrect, index: " + reference + ", offset: " + offset, 
                       validator.putAndGetAt(memory, i, reference, offset));
            
            references[i] = reference; 
            offsets[i]    = offset;
        }
        
        for (int i = 0; i < numberOfPuts; i++)
        {
            long reference = references[i];
            int  offset    = offsets[i];

            assertTrue("value " + i + " is incorrect, reference: " + reference + ", offset: " + offset, 
                       validator.validateGetAt(memory, reference, offset));
        }
    }
    
    private static int randomOffset(Random random, Memory memory, int typeSize, int alignment)
    {
        return (random.nextInt((memory.getEntrySize() / typeSize) - (alignment == 0 ? 0 : 1)) * typeSize) + alignment;
    }
    
    public static MemoryAllocator getMemoryMappedAllocator()
    {
        return new MemoryAllocator()
        {
            @Override
            public Memory allocate(int size, int chunkSize)
            {
                RandomAccessFile raf = null;
                try
                {
                    File f = File.createTempFile(System.getProperty("user.name") + "-", ".map");
                    f.deleteOnExit();
                    
                    raf = new RandomAccessFile(f, "rw");
                    MappedByteBuffer map = raf.getChannel().map(MapMode.READ_WRITE, 0, size * chunkSize);
                    return DirectMemory.fromByteBuffer(map, size, chunkSize);
                }
                catch (IOException e)
                {
                    throw new RuntimeException(e);
                }
                finally
                {
                    close(raf);
                }
            }
        };
    }
    
    private static void close(Closeable c)
    {
        try
        {
            c.close();
        }
        catch (IOException e)
        {
            // Swallow.
        }
    }
}
