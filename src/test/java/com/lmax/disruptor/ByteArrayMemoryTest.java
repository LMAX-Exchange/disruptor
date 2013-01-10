package com.lmax.disruptor;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Random;

import org.junit.Test;

import com.lmax.disruptor.util.Bits;


public class ByteArrayMemoryTest
{
    private static final int OFFSET = 1;
    private static final int INDEX = 0;
    
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
    
    private static class ByteArrayValidator implements TypeValidator
    {
        private static final int BYTE_ARRAY_SIZE = 16;
        byte[][] input;
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
            
            byte[] stored = memory.getBytes(index, offset, BYTE_ARRAY_SIZE);
            
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
        long[] input;
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
        int[] input;
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
        Memory memory = ByteArrayMemory.newInstance(size, chunkSize);
        
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
    
    private int randomOffset(Random random, Memory memory, int typeSize, int alignment)
    {
        return (random.nextInt((memory.getChunkSize() / typeSize) - (alignment == 0 ? 0 : 1)) * typeSize) + alignment;
    }    
}
