package com.lmax.disruptor;

public interface RingBuffer<E>
{
    SequenceBarrier newBarrier(final Sequence... sequencesToTrack);

    void setGatingSequences(Sequence... gatingSequences);

    long getCursor();
    
    int getBufferSize();
    
    long next();

    E get(long sequence);
    
    void publish(long sequence);
}