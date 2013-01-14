package com.lmax.disruptor;

import static com.lmax.disruptor.util.Util.isPowerOfTwo;
import static java.lang.String.format;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Bits;
import com.lmax.disruptor.util.Util;

public class DirectMemory implements Memory
{
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final Unsafe UNSAFE = Util.getUnsafe();
    private static final long BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    
    @SuppressWarnings("unused")
    private final ByteBuffer buffer;
    private final byte[] array;
    private final long   addressOffset;
    private final int    size;
    private final int    chunkSize;
    private final int    mask;

    public DirectMemory(ByteBuffer buffer, byte[] array, long addressOffset, int size, int chunkSize)
    {
        if (!isPowerOfTwo(size))
        {
            throw new IllegalArgumentException("size must be a power of two");
        }
        
        this.buffer          = buffer;
        this.array           = array;
        this.addressOffset   = addressOffset;
        this.size            = size;
        this.chunkSize       = chunkSize;
        this.mask            = size - 1;
    }

    public static Memory newInstance(int size, int chunkSize)
    {
        int numBytes = size * chunkSize;
        byte[] array = new byte[numBytes];
        return new DirectMemory(null, array, BYTE_ARRAY_OFFSET, size, chunkSize);
    }
    
    public static Memory newDirectInstance(int size, int chunkSize)
    {
        int numBytes = size * chunkSize;
        ByteBuffer buffer = ByteBuffer.allocateDirect(numBytes);
        
        long addressOffset = Util.getAddressFromDirectByteBuffer(buffer);
        
        return new DirectMemory(buffer, null, addressOffset, size, chunkSize);
    }
    
    public static Memory fromByteBuffer(ByteBuffer buffer, int size, int chunkSize)
    {
        if (buffer.capacity() != size * chunkSize)
        {
            String msg = String.format("ByteBuffer.capacity(%d) must be equal to size(%d) * chunkSize(%d)",
                                       buffer.capacity(), size, chunkSize);
            throw new IllegalArgumentException(msg);
        }
        
        if (buffer.hasArray())
        {
            byte[] array = buffer.array();
            return new DirectMemory(null, array, BYTE_ARRAY_OFFSET, size, chunkSize);
        }
        else
        {
            long addressOffset = Util.getAddressFromDirectByteBuffer(buffer);
            return new DirectMemory(buffer, null, addressOffset, size, chunkSize);
        }
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
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
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
    public long getVolatileLong(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getLongVolatile(array, addressOffsetOf(index, offset));
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
    public void putOrderedLong(int index, int offset, long value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putOrderedLong(array, addressOffsetOf(index, offset), value);
    }
    
    @Override
    public double getDouble(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofDouble()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getDouble(array, addressOffsetOf(index, offset));
    }
    
    @Override
    public void putDouble(int index, int offset, double value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putDouble(array, addressOffsetOf(index, offset), value);
    }
    
    @Override
    public float getFloat(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofFloat()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getFloat(array, addressOffsetOf(index, offset));
    }
    
    @Override
    public void putFloat(int index, int offset, float value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putFloat(array, addressOffsetOf(index, offset), value);
    }
    
    @Override
    public short getShort(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofShort()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getShort(array, addressOffsetOf(index, offset));
    }
    
    @Override
    public void putShort(int index, int offset, short value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putShort(array, addressOffsetOf(index, offset), value);
    }
    
    @Override
    public char getChar(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofChar()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getChar(array, addressOffsetOf(index, offset));
    }
    
    @Override
    public void putChar(int index, int offset, char value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putChar(array, addressOffsetOf(index, offset), value);
    }
    
    @Override
    public byte getByte(int index, int offset)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeofByte()) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        return UNSAFE.getByte(array, addressOffsetOf(index, offset));
    }
    
    @Override
    public void putByte(int index, int offset, byte value)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= chunkSize : 
            format("offset: %d, chunkSize: %d", offset, chunkSize);
        
        UNSAFE.putByte(array, addressOffsetOf(index, offset), value);
    }

    @Override
    public int getBytes(int index, int offset, int length, byte[] data)
    {
        assert 0 <= index && index < size : format("index: %d, size: %d", index, size);
        
        length = Math.min(length, chunkSize - offset);
        
        if (length < 1)
        {
            return 0;
        }
        
        UNSAFE.copyMemory(array, addressOffsetOf(index, offset), data, BYTE_ARRAY_OFFSET, length);
        
        return length;
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
        
        UNSAFE.copyMemory(array, addressOffsetOf(index, offset), data, BYTE_ARRAY_OFFSET, length);
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
        
        UNSAFE.copyMemory(value, BYTE_ARRAY_OFFSET + arrayOffset, array, addressOffsetOf(index, offset), length);
        
        return length;
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
        return addressOffset + (index * chunkSize) + offset;
    }

    public static MemoryAllocator getByteArrayAllocator()
    {
        // TODO: Make static.
        return new MemoryAllocator()
        {
            @Override
            public Memory allocate(int size, int chunkSize)
            {
                return DirectMemory.newInstance(size, chunkSize);
            }
        };
    }

    public static MemoryAllocator getByteBufferAllocator()
    {
        // TODO: Make static.
        return new MemoryAllocator()
        {
            @Override
            public Memory allocate(int size, int chunkSize)
            {
                return DirectMemory.newDirectInstance(size, chunkSize);
            }
        };
    }    
}
