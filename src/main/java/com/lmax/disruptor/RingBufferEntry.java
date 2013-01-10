package com.lmax.disruptor;

public interface RingBufferEntry
{
    int size();
    
    long getSequence();
    void setSequence(long value);

    void move(Memory memory, int index);
}
