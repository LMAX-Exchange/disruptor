package com.lmax.disruptor;

import static com.lmax.disruptor.util.Util.isPowerOfTwo;
import static java.lang.String.format;

import java.nio.ByteBuffer;

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
    private final int    entryCount;
    private final int    entrySize;
    private final int    mask;
    private final long   upperBound;

    public DirectMemory(ByteBuffer buffer, byte[] array, long addressOffset, int entryCount, int entrySize)
    {
        if (!isPowerOfTwo(entryCount))
        {
            throw new IllegalArgumentException("size must be a power of two");
        }
        
        this.buffer          = buffer;
        this.array           = array;
        this.addressOffset   = addressOffset;
        this.entryCount      = entryCount;
        this.entrySize       = entrySize;
        this.mask            = entryCount - 1;
        
        this.upperBound      = (entryCount * (long) entrySize) + addressOffset;
    }

    public static Memory newInstance(int entryCount, int entrySize)
    {
        int numBytes = entryCount * entrySize;
        byte[] array = new byte[numBytes];
        return new DirectMemory(null, array, BYTE_ARRAY_OFFSET, entryCount, entrySize);
    }
    
    public static Memory newDirectInstance(int entryCount, int entrySize)
    {
        int numBytes = entryCount * entrySize;
        ByteBuffer buffer = ByteBuffer.allocateDirect(numBytes);
        
        long addressOffset = Util.getAddressFromDirectByteBuffer(buffer);
        
        return new DirectMemory(buffer, null, addressOffset, entryCount, entrySize);
    }
    
    public static Memory fromByteBuffer(ByteBuffer buffer, int entryCount, int entrySize)
    {
        if (buffer.capacity() != entryCount * entrySize)
        {
            String msg = String.format("ByteBuffer.capacity(%d) must be equal to size(%d) * entrySize(%d)",
                                       buffer.capacity(), entryCount, entrySize);
            throw new IllegalArgumentException(msg);
        }
        
        if (buffer.hasArray())
        {
            byte[] array = buffer.array();
            return new DirectMemory(null, array, BYTE_ARRAY_OFFSET, entryCount, entrySize);
        }
        else
        {
            long addressOffset = Util.getAddressFromDirectByteBuffer(buffer);
            return new DirectMemory(buffer, null, addressOffset, entryCount, entrySize);
        }
    }

    @Override
    public int getInt(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofInt()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getInt(array, addressOffsetOf(reference, offset));
    }

    @Override
    public void putInt(long reference, int offset, int value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize); 
        
        UNSAFE.putInt(array, addressOffsetOf(reference, offset), value);
    }

    @Override
    public long getLong(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getLong(array, addressOffsetOf(reference, offset));
    }

    @Override
    public long getVolatileLong(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getLongVolatile(array, addressOffsetOf(reference, offset));
    }

    @Override
    public void putLong(long reference, int offset, long value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofLong()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putLong(array, addressOffsetOf(reference, offset), value);
    }

    @Override
    public void putOrderedLong(long reference, int offset, long value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putOrderedLong(array, addressOffsetOf(reference, offset), value);
    }
    
    @Override
    public double getDouble(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofDouble()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getDouble(array, addressOffsetOf(reference, offset));
    }
    
    @Override
    public void putDouble(long reference, int offset, double value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putDouble(array, addressOffsetOf(reference, offset), value);
    }
    
    @Override
    public float getFloat(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofFloat()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getFloat(array, addressOffsetOf(reference, offset));
    }
    
    @Override
    public void putFloat(long reference, int offset, float value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putFloat(array, addressOffsetOf(reference, offset), value);
    }
    
    @Override
    public short getShort(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofShort()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getShort(array, addressOffsetOf(reference, offset));
    }
    
    @Override
    public void putShort(long reference, int offset, short value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putShort(array, addressOffsetOf(reference, offset), value);
    }
    
    @Override
    public char getChar(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofChar()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getChar(array, addressOffsetOf(reference, offset));
    }
    
    @Override
    public void putChar(long reference, int offset, char value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putChar(array, addressOffsetOf(reference, offset), value);
    }
    
    @Override
    public byte getByte(long reference, int offset)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeofByte()) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        return UNSAFE.getByte(array, addressOffsetOf(reference, offset));
    }
    
    @Override
    public void putByte(long reference, int offset, byte value)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        assert 0 <= offset && (offset + Bits.sizeof(value)) <= entrySize : 
            format("offset: %d, entrySize: %d", offset, entrySize);
        
        UNSAFE.putByte(array, addressOffsetOf(reference, offset), value);
    }

    @Override
    public int getBytes(long reference, int offset, int length, byte[] data)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        
        length = Math.min(length, entrySize - offset);
        
        if (length < 1)
        {
            return 0;
        }
        
        UNSAFE.copyMemory(array, addressOffsetOf(reference, offset), data, BYTE_ARRAY_OFFSET, length);
        
        return length;
    }
    
    @Override
    public byte[] getBytes(long reference, int offset, int length)
    {
        assert addressOffset <= reference && reference < upperBound :
            format("index: %d, size: %d", reference, entryCount);
        
        length = Math.min(length, entrySize - offset);
        
        if (length < 1)
        {
            return EMPTY_BYTE_ARRAY;
        }
        
        byte[] data = new byte[length];
        
        UNSAFE.copyMemory(array, addressOffsetOf(reference, offset), data, BYTE_ARRAY_OFFSET, length);
        return data;
    }

    @Override
    public int putBytes(long reference, int offset, byte[] value, int arrayOffset, int length)
    {
        assert addressOffset <= reference && reference < upperBound : 
            format("index: %d, size: %d", reference, entryCount);
        
        length = Math.min(length, entrySize - offset);
        
        if (length < 1)
        {
            return 0;
        }
        
        UNSAFE.copyMemory(value, BYTE_ARRAY_OFFSET + arrayOffset, array, addressOffsetOf(reference, offset), length);
        
        return length;
    }

    @Override
    public int getEntryCount()
    {
        return entryCount;
    }

    @Override
    public int getEntrySize()
    {
        return entrySize;
    }
    
    @Override
    public long indexOf(long next)
    {
        return (next & mask) * entrySize + addressOffset;
    }
    
    private long addressOffsetOf(long reference, int offset)
    {
        return reference + offset;
    }

    public static MemoryAllocator getByteArrayAllocator()
    {
        // TODO: Make static.
        return new MemoryAllocator()
        {
            @Override
            public Memory allocate(int size, int entrySize)
            {
                return DirectMemory.newInstance(size, entrySize);
            }
        };
    }

    public static MemoryAllocator getByteBufferAllocator()
    {
        // TODO: Make static.
        return new MemoryAllocator()
        {
            @Override
            public Memory allocate(int size, int entrySize)
            {
                return DirectMemory.newDirectInstance(size, entrySize);
            }
        };
    }    
}
