package com.lmax.disruptor;

import static com.lmax.disruptor.DirectMemory.fromByteBuffer;

import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class ExpandableMemory implements Memory
{
    private static final long MAP_INDEX_SHIFT      = 32L;
    private static final long CHILD_REFERENCE_MASK = (1L << 32) - 1;
    private static final int  UNSET_VALUE          = -1;

    private final FileChannel channel;
    private final Sequence    initialSequence = new Sequence(UNSET_VALUE);
    
    private final int entrySize;
    private final int initialEntryCount;
    
    private volatile long entryCount;
    private volatile AtomicReferenceArray<DirectMemory> maps = new AtomicReferenceArray<DirectMemory>(4);

    public ExpandableMemory(FileChannel channel, int entryCount, int entrySize) throws IOException
    {
        this.channel           = channel;
        this.initialEntryCount = entryCount;
        this.entryCount        = 0;
        this.entrySize         = entrySize;
    }

    public int getEntryCount()
    {
        return initialEntryCount;
    }

    public int getEntrySize()
    {
        return entrySize;
    }

    public long referenceFor(long sequence)
    {
        long mapIndex          = mapIndexFromSequence(sequence);
        long delegateReference = maps.get((int) mapIndex).referenceFor(sequence);
        
        return (mapIndex << MAP_INDEX_SHIFT) | delegateReference; 
    }

    @Override
    public long referenceFor(long sequence, boolean allowExpansion)
    {
        while (allowExpansion && outsideBounds(sequence))
        {
            allocateMemory(sequence);
        }
        
        return referenceFor(sequence);
    }

    private boolean outsideBounds(long sequence)
    {
        long lowerBoundSequence = getLowerBoundSequence(sequence);
        return sequence >= lowerBoundSequence + entryCount;
    }

    private long mapIndexFromSequence(long sequence)
    {
         // TODO: Use a mask.        
        return (sequence - initialSequence.get()) / initialEntryCount;
    }
    
    private int mapIndexFromReference(long reference)
    {
        return (int) (reference >>> MAP_INDEX_SHIFT);
    }
    
    private long delegateReferenceFor(long reference)
    {
        return reference & CHILD_REFERENCE_MASK;
    }

    private void allocateMemory(long sequence)
    {
        synchronized (channel)
        {
            // Did another thread map the additional memory.
            // Little bit of double checked locking.
            if (!outsideBounds(sequence))
            {
                return;
            }
            
            int  mapIndex      = (int) (entryCount / initialEntryCount);
            int  memorySize    = initialEntryCount * entrySize;
            long startPosition = entryCount * entrySize;
            try
            {
                MappedByteBuffer map    = channel.map(MapMode.READ_WRITE, startPosition, memorySize);
                DirectMemory     memory = fromByteBuffer(map, initialEntryCount, entrySize);
                
                entryCount += initialEntryCount;
                
                if (mapIndex >= maps.length())
                {
                    AtomicReferenceArray<DirectMemory> newMaps = 
                            new AtomicReferenceArray<DirectMemory>(maps.length() << 1);
                    for (int i = 0, n = maps.length(); i < n; i++)
                    {
                        newMaps.set(i, maps.get(i));
                    }
                    
                    maps = newMaps;
                }
                maps.set(mapIndex, memory);
            }
            catch (IOException e)
            {
                throw new IllegalStateException(e);
            }
        }
    }

    private long getLowerBoundSequence(long sequence)
    {
        return initialSequence.setOnce(UNSET_VALUE, sequence);
    }    
    
    private Memory getDelegate(long reference)
    {
        int mapIndex = mapIndexFromReference(reference);
        DirectMemory delegate = maps.get(mapIndex);
        
        assert delegate != null : String.format("delegate is null, reference: %d, mapIndex: %d, maps.length: %d", 
                                                reference, mapIndex, maps.length());
        
        return delegate;
    }

    public byte getByte(long reference, int offset)
    {
        return getDelegate(reference).getByte(delegateReferenceFor(reference), offset);
    }

    public void putByte(long reference, int offset, byte value)
    {
        getDelegate(reference).putByte(delegateReferenceFor(reference), offset, value);
    }

    public short getShort(long reference, int offset)
    {
        return getDelegate(reference).getShort(delegateReferenceFor(reference), offset);
    }

    public void putShort(long reference, int offset, short value)
    {
        getDelegate(reference).putShort(delegateReferenceFor(reference), offset, value);
    }

    public char getChar(long reference, int offset)
    {
        return getDelegate(reference).getChar(delegateReferenceFor(reference), offset);
    }

    public void putChar(long reference, int offset, char value)
    {
        getDelegate(reference).putChar(delegateReferenceFor(reference), offset, value);
    }

    public int getInt(long reference, int offset)
    {
        return getDelegate(reference).getInt(delegateReferenceFor(reference), offset);
    }

    public void putInt(long reference, int offset, int value)
    {
        getDelegate(reference).putInt(delegateReferenceFor(reference), offset, value);
    }

    public long getLong(long reference, int offset)
    {
        return getDelegate(reference).getLong(delegateReferenceFor(reference), offset);
    }

    public long getVolatileLong(long reference, int offset)
    {
        return getDelegate(reference).getVolatileLong(delegateReferenceFor(reference), offset);
    }

    public void putLong(long reference, int offset, long value)
    {
        getDelegate(reference).putLong(delegateReferenceFor(reference), offset, value);
    }

    public void putOrderedLong(long reference, int offset, long value)
    {
        getDelegate(reference).putOrderedLong(delegateReferenceFor(reference), offset, value);
    }

    public float getFloat(long reference, int offset)
    {
        return getDelegate(reference).getFloat(delegateReferenceFor(reference), offset);
    }

    public void putFloat(long reference, int offset, float value)
    {
        getDelegate(reference).putFloat(delegateReferenceFor(reference), offset, value);
    }

    public double getDouble(long reference, int offset)
    {
        return getDelegate(reference).getDouble(delegateReferenceFor(reference), offset);
    }

    public void putDouble(long reference, int offset, double value)
    {
        getDelegate(reference).putDouble(delegateReferenceFor(reference), offset, value);
    }

    public byte[] getBytes(long reference, int offset, int length)
    {
        return getDelegate(reference).getBytes(delegateReferenceFor(reference), offset, length);
    }

    public int getBytes(long reference, int offset, int length, byte[] data)
    {
        return getDelegate(reference).getBytes(delegateReferenceFor(reference), offset, length, data);
    }

    public int putBytes(long reference, int offset, byte[] value, int arrayOffset, int length)
    {
        return getDelegate(reference).putBytes(delegateReferenceFor(reference), offset, value, arrayOffset, length);
    }
}
