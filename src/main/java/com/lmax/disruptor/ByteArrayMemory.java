package com.lmax.disruptor;

import static com.lmax.disruptor.util.Util.isPowerOfTwo;
import static java.lang.String.format;
import sun.misc.Unsafe;

import com.lmax.disruptor.util.Bits;
import com.lmax.disruptor.util.Util;

public class ByteArrayMemory implements Memory
{
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Unsafe UNSAFE = Util.getUnsafe();
    private static final long BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    
    private final byte[] array;
    private final long   byteArrayOffset;
    private final int    size;
    private final int    chunkSize;
    private final int    mask;

    public ByteArrayMemory(byte[] array, long byteArrayOffset, int size, int chunkSize)
    {
        if (!isPowerOfTwo(size))
        {
            throw new IllegalArgumentException("size must be a power of two");
        }
        
        this.array           = array;
        this.byteArrayOffset = byteArrayOffset;
        this.size            = size;
        this.chunkSize       = chunkSize;
        this.mask            = size - 1;
    }

    @Override
    public int getInt(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofInt()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getInt(array, addressOffsetOf(index, offset));
    }

    @Override
    public void putInt(int index, int offset, int value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofInt()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize); 
        
        UNSAFE.putInt(array, addressOffsetOf(index, offset), value);
    }

    @Override
    public long getLong(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getLong(array, addressOffsetOf(index, offset));
    }

    @Override
    public void putLong(int index, int offset, long value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putLong(array, addressOffsetOf(index, offset), value);
    }

    @Override
    public byte[] getBytes(int index, int offset, int length)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        
        length = Math.min(length, chunkSize - offset);
        
        if (length < 1)
        {
            return EMPTY_BYTE_ARRAY;
        }
        
        byte[] data = new byte[length];
        
        UNSAFE.copyMemory(array, addressOffsetOf(index, offset), data, byteArrayOffset, length);
        return data;
    }

    @Override
    public int putBytes(int index, int offset, byte[] value, int arrayOffset, int length)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        
        length = Math.min(length, chunkSize - offset);
        
        if (length < 1)
        {
            return 0;
        }
        
        UNSAFE.copyMemory(value, byteArrayOffset + arrayOffset, array, addressOffsetOf(index, offset), length);
        
        return length;
    }

    public static Memory newInstance(int size, int chunkSize)
    {
        int numBytes = size * chunkSize;
        byte[] array = new byte[numBytes];
        return new ByteArrayMemory(array, BYTE_ARRAY_OFFSET, size, chunkSize);
    }

    @Override
    public int getSize()
    {
        return size;
    }

    @Override
    public int getChunkSize()
    {
        return chunkSize;
    }
    
    @Override
    public int indexOf(long next)
    {
        return mask & ((int) next);
    }
    
    private long addressOffsetOf(int index, int offset)
    {
        return byteArrayOffset + (index * chunkSize) + offset;
    }
}
