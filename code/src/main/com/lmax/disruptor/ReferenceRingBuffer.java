package com.lmax.disruptor;

public class ReferenceRingBuffer<E> extends RingBuffer<E> implements ReferencePublisher<E>
{
    protected final Object[] entries;
    
    public ReferenceRingBuffer(Sequencer sequencer)
    {
        super(sequencer);
        entries = new Object[sequencer.getBufferSize()];
    }

    @SuppressWarnings("unchecked")
    @Override
    public E get(long sequence)
    {
        return (E)entries[(int)sequence & indexMask];
    }

    /**
     * @see com.lmax.disruptor.ReferencePublisher#put(E)
     */
    @Override
    public void put(E event)
    {
        long sequence = sequencer.next();
        entries[(int) sequence & indexMask] = event;
        sequencer.publish(sequence);
    }

    /**
     * @see com.lmax.disruptor.ReferencePublisher#offer(E)
     */
    @Override
    public boolean offer(E event)
    {
        try
        {
            long sequence = sequencer.tryNext(1);
            entries[(int) sequence & indexMask] = event;
            sequencer.publish(sequence);
            return true;
        }
        catch (InsufficientCapacityException e)
        {
            return false;
        }
    }
}
