package com.lmax.disruptor.support;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Sequencer;

public class LongRingBuffer extends RingBuffer<Long>
{
    private final int indexMask;
    private final long[] entries;

    public LongRingBuffer(Sequencer sequencer)
    {
        super(sequencer);
        int bufferSize = sequencer.getBufferSize();
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        indexMask = bufferSize - 1;
        entries = new long[bufferSize];
    }
    
    public void put(long l)
    {
        long sequence = sequencer.next();
        entries[(int)sequence & indexMask] = l;
        sequencer.publish(l);
    }
    
    public long getLong(long sequence)
    {
        sequencer.ensureAvailable(sequence);
        return entries[(int)sequence & indexMask];
    }
    
    @Override
    public Long get(long sequence)
    {
        return getLong(sequence);
    }
}
