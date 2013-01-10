package com.lmax.disruptor;

import com.lmax.disruptor.dsl.ProducerType;


public class OffHeapRingBuffer<T extends RingBufferEntry>
{
    private final Sequencer sequencer;
    private final EntryFactory<T> factory;
    private final Sequence cursorSequence;
    private final WaitStrategy waitStrategy;
    
    private volatile Sequence[] gatingSequences = new Sequence[0];
    private volatile Memory     memory;
    
    public OffHeapRingBuffer(Sequence        cursorSequence,
                             Sequencer       sequencer,
                             WaitStrategy    waitStrategy,
                             Memory          memory, 
                             EntryFactory<T> factory)
    {
        this.cursorSequence = cursorSequence;
        this.sequencer      = sequencer;
        this.waitStrategy   = waitStrategy;
        this.memory         = memory;
        this.factory        = factory;
    }
    
    public static <T extends RingBufferEntry> OffHeapRingBuffer<T> newMultiProducer(WaitStrategy    waitStrategy, 
                                                                                    EntryFactory<T> factory, 
                                                                                    int size, int chunkSize)
    {
        MultiProducerSequencer sequencer  = new MultiProducerSequencer(size, waitStrategy);

        Memory memory = ByteArrayMemory.newInstance(size, chunkSize);
        
        return new OffHeapRingBuffer<T>(sequencer.getCursorSequence(), sequencer, waitStrategy, memory, factory);
    }
    
    public static <T extends RingBufferEntry> OffHeapRingBuffer<T> newSingleProducer(WaitStrategy    waitStrategy, 
                                                                                     EntryFactory<T> factory, 
                                                                                     int size, int chunkSize)
    {
        SingleProducerSequencer sequencer = new SingleProducerSequencer(size, waitStrategy);

        Memory memory = ByteArrayMemory.newInstance(size, chunkSize);
        
        return new OffHeapRingBuffer<T>(new Sequence(), sequencer, waitStrategy, memory, factory);
    }
    
    public static <T extends RingBufferEntry> OffHeapRingBuffer<T> newInstance(ProducerType    producerType,
                                                                               WaitStrategy    waitStrategy,
                                                                               EntryFactory<T> factory,
                                                                               int size, int chunkSize)
    {
        switch (producerType)
        {
        case SINGLE:
            return newSingleProducer(waitStrategy, factory, size, chunkSize);
        case MULTI:
            return newMultiProducer(waitStrategy, factory, size, chunkSize);
        default:
            throw new IllegalStateException(producerType.toString());
        }
        
    }
    
    private long next()
    {
        return sequencer.next(gatingSequences);        
    }

    private T getPreallocated(T entry, long sequence)
    {
        if (entry == null)
        {
            entry = factory.newInstance();
        }
        
        // TODO: Handle allocated memory roll-over
        entry.move(memory, memory.indexOf(sequence));
        
        return entry;
    }

    private void publish(T entry, long sequence)
    {
        entry.setSequence(sequence);
        
        if (sequencer instanceof SingleProducerSequencer)
        {
            cursorSequence.set(sequence);
        }
        
        waitStrategy.signalAllWhenBlocking();
    }

    public T getPublished(T entry, long sequence)
    {
        if (entry == null)
        {
            entry = factory.newInstance();
        }
        
        entry.move(memory, memory.indexOf(sequence));
        
        while (entry.getSequence() != sequence)
        {
            // Busy spin
        }
        
        return entry;
    }

    public Producer<T> createProducer()
    {
        return new OffHeapProducer<T>(this);
    }
    
    private static class OffHeapProducer<E extends RingBufferEntry> implements Producer<E>
    {
        private final OffHeapRingBuffer<E> ringBuffer;
        
        private E    current  = null;
        private long sequence = -1;
        
        public OffHeapProducer(OffHeapRingBuffer<E> ringBuffer)
        {
            this.ringBuffer = ringBuffer;
        }
        
        @Override
        public E next()
        {
            sequence = ringBuffer.next();
            current  = ringBuffer.getPreallocated(current, sequence);
            
            return current;
        }
        
        @Override
        public long currentSequence()
        {
            return sequence;
        }

        @Override
        public void publish()
        {
            if (current != null)
            {
                ringBuffer.publish(current, sequence);
            }
        }
    }

    public SequenceBarrier newBarrier(Sequence...sequencesToTrack)
    {
        return new ProcessingSequenceBarrier(waitStrategy, cursorSequence, sequencesToTrack);
    }

    public DataSource<T> createDataSource()
    {
        return new OffHeapDataSource<T>(this);
    }
    
    private static class OffHeapDataSource<T extends RingBufferEntry> implements DataSource<T>
    {
        private final OffHeapRingBuffer<T> ringBuffer;
        private final T entry;

        public OffHeapDataSource(OffHeapRingBuffer<T> ringBuffer)
        {
            this.ringBuffer = ringBuffer;
            this.entry = ringBuffer.factory.newInstance();
        }
        
        @Override
        public T getPublished(long sequence)
        {
            return ringBuffer.getPublished(entry, sequence);
        }
    }
}
