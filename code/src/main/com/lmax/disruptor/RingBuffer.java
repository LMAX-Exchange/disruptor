package com.lmax.disruptor;

public abstract class RingBuffer<E>
{
    protected final Sequencer sequencer;
    protected final int indexMask;
    
    /**
     * Construct a RingBuffer with the full option set.
     *
     * @param sequencer sequencer to handle the ordering of events moving through the RingBuffer.
     *
     * @throws IllegalArgumentException if bufferSize is not a power of 2
     */
    public RingBuffer(Sequencer sequencer)
    {
        this.sequencer = sequencer;
        int bufferSize = sequencer.getBufferSize();
        if (Integer.bitCount(bufferSize) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        indexMask = bufferSize - 1;
    }

    public final SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
    {
        return sequencer.newBarrier(sequencesToTrack);
    }

    public final void setGatingSequences(Sequence... gatingSequences)
    {
        sequencer.setGatingSequences(gatingSequences);
    }

    public final long getCursor()
    {
        return sequencer.getCursor();
    }
    
    public Sequencer getSequencer()
    {
        return sequencer;
    }
    
    public BatchDescriptor newBatchDescriptor(int batchSize)
    {
        return sequencer.newBatchDescriptor(batchSize);
    }

    public int getBufferSize()
    {
        return sequencer.getBufferSize();
    }
    
    public abstract E get(long sequence);
}