package com.lmax.disruptor;

import com.lmax.disruptor.util.Bits;

public abstract class RingBufferEntryBase
{
    private static final int SEQUENCE_OFFSET = 0;
    protected static final int BASE_OFFSET = SEQUENCE_OFFSET + Bits.sizeofLong();
    
    protected Memory memory;
    protected int index;
    
    public void move(Memory memory, int index)
    {
        this.memory = memory;
        this.index  = index;
    }

    public long getSequence()
    {
        return memory.getLong(index, SEQUENCE_OFFSET);
    }

    public void setSequence(long value)
    {
        memory.putLong(index, SEQUENCE_OFFSET, value);
    }
}
