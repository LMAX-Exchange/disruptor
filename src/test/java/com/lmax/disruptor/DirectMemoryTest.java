package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

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
    private static final int OFFSET = 1;
    private static final int INDEX  = 0;
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
         { DirectMemory.getByteBufferAllocator() }
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
    
    private static class ByteArrayValidator implements TypeValidator
    {
        private static final int BYTE_ARRAY_SIZE = 16;
        byte[][]   input;
        byte[][][] values;

        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new byte[numberOfPuts][BYTE_ARRAY_SIZE]);
            values = new byte[size][chunkSize][BYTE_ARRAY_SIZE]; 
        }

        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putBytes(index, offset, input[i], 0, input[i].length);
            System.arraycopy(input[i], 0, values[index][offset], 0, values[index][offset].length);
            byte[] stored = new byte[16];
            int amountRead = memory.getBytes(index, offset, BYTE_ARRAY_SIZE, stored);
            
            assertThat(amountRead, is(BYTE_ARRAY_SIZE));
            
            return Arrays.equals(stored, input[i]);
        }

        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            byte[] stored = memory.getBytes(index, offset, BYTE_ARRAY_SIZE);
            return Arrays.equals(stored, values[index][offset]);
        }

        @Override
        public int getTypeSize()
        {
            return BYTE_ARRAY_SIZE;
        }        
    }
    
    private static class LongValidator implements TypeValidator
    {
        long[]   input;
        long[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new long[numberOfPuts]);
            values = new long[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putLong(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getLong(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getLong(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofLong();
        }
    }
    
    private static class IntValidator implements TypeValidator
    {
        int[]   input;
        int[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new int[numberOfPuts]);
            values = new int[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putInt(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getInt(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getInt(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofInt();
        }
    }
    
    private static class DoubleValidator implements TypeValidator
    {
        double[]   input;
        double[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new double[numberOfPuts]);
            values = new double[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putDouble(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getDouble(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getDouble(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofDouble();
        }
    }
    
    private static class FloatValidator implements TypeValidator
    {
        float[]   input;
        float[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new float[numberOfPuts]);
            values = new float[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putFloat(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getFloat(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getFloat(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofFloat();
        }
    }
    
    private static class ShortValidator implements TypeValidator
    {
        short[]   input;
        short[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new short[numberOfPuts]);
            values = new short[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putShort(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getShort(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getShort(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofShort();
        }
    }
    
    private static class CharValidator implements TypeValidator
    {
        char[]   input;
        char[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new char[numberOfPuts]);
            values = new char[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putChar(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getChar(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getChar(index, offset) == values[index][offset];
        }
        
        @Override
        public int getTypeSize()
        {
            return Bits.sizeofChar();
        }
    }
    
    private static class ByteValidator implements TypeValidator
    {
        byte[]   input;
        byte[][] values;
        
        @Override
        public void init(Random r, int numberOfPuts, int size, int chunkSize)
        {
            input  = Randoms.generateArray(r, new byte[numberOfPuts]);
            values = new byte[size][chunkSize];
        }
        
        @Override
        public boolean putAndGetAt(Memory memory, int i, int index, int offset)
        {
            memory.putByte(index, offset, input[i]);
            values[index][offset] = input[i];
            
            return memory.getByte(index, offset) == input[i];            
        }
        
        @Override
        public boolean validateGetAt(Memory memory, int index, int offset)
        {
            return memory.getByte(index, offset) == values[index][offset];
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
        
        int typeSize         = validator.getTypeSize();
        int alignment        = random.nextInt(typeSize);
        int[][] accessPoints = new int[2][numberOfPuts];
        
        validator.init(random, numberOfPuts, size, chunkSize);
        
        for (int i = 0; i < numberOfPuts; i++)
        {
            int index = random.nextInt(memory.getSize());
            int offset = randomOffset(random, memory, typeSize, alignment);
            
            assertTrue("value " + i + " is incorrect, index: " + index + ", offset: " + offset, 
                       validator.putAndGetAt(memory, i, index, offset));
            
            accessPoints[INDEX][i] = index; 
            accessPoints[OFFSET][i] = offset;
        }
        
        for (int i = 0; i < numberOfPuts; i++)
        {
            int index = accessPoints[INDEX][i];
            int offset = accessPoints[OFFSET][i];

            assertTrue("value " + i + " is incorrect, index: " + index + ", offset: " + offset, 
                       validator.validateGetAt(memory, index, offset));
        }
    }
    
    private static int randomOffset(Random random, Memory memory, int typeSize, int alignment)
    {
        return (random.nextInt((memory.getChunkSize() / typeSize) - (alignment == 0 ? 0 : 1)) * typeSize) + alignment;
    }    
}
