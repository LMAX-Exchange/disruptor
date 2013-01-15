package com.lmax.disruptor;

import com.lmax.disruptor.util.Bits;

public abstract class RingBufferEntryBase implements RingBufferEntry
{
    private static final int SEQUENCE_OFFSET = 0;
    protected static final int BASE_OFFSET = SEQUENCE_OFFSET + Bits.sizeofLong();
    
    protected Memory memory;
    protected long reference;
    
    public void move(Memory memory, long index)
    {
        this.memory = memory;
        this.reference  = index;
    }

    public long getSequence()
    {
        return memory.getVolatileLong(reference, SEQUENCE_OFFSET);
    }

    public void setSequence(long value)
    {
        memory.putOrderedLong(reference, SEQUENCE_OFFSET, value);
    }
}
