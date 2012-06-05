package com.lmax.disruptor;

public class ReferenceRingBuffer<E> extends RingBuffer<E>
{
    public ReferenceRingBuffer(Sequencer sequencer)
    {
        super(sequencer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E get(long sequence)
    {
        return (E)entries[(int)sequence & indexMask];
    }

    public void put(E event)
    {
        long sequence = sequencer.next();
        entries[(int) sequence & indexMask] = event;
        sequencer.publish(sequence);
    }

    public boolean offset(E event)
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
