package com.lmax.disruptor.immutable;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BatchEventProcessorBuilder;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.Sequencer;

public class CustomRingBuffer<T> implements DataProvider<EventAccessor<T>>, EventAccessor<T>
{
    private static final class AccessorEventHandler<T> implements EventHandler<EventAccessor<T>>
    {
        private final EventHandler<T> handler;

        private AccessorEventHandler(final EventHandler<T> handler)
        {
            this.handler = handler;
        }

        @Override
        public void onEvent(final EventAccessor<T> accessor, final long sequence, final boolean endOfBatch) throws Exception
        {
            this.handler.onEvent(accessor.take(sequence), sequence, endOfBatch);
        }

        @Override
        public void onShutdown()
        {
            handler.onShutdown();
        }

        @Override
        public void onStart()
        {
            handler.onStart();
        }
    }

    private final Sequencer sequencer;
    private final Object[] buffer;
    private final int mask;

    public CustomRingBuffer(final Sequencer sequencer)
    {
        this.sequencer = sequencer;
        buffer = new Object[sequencer.getBufferSize()];
        mask = sequencer.getBufferSize() - 1;
    }

    private int index(final long sequence)
    {
        return (int) sequence & mask;
    }

    public void put(final T e)
    {
        long next = sequencer.next();
        buffer[index(next)] = e;
        sequencer.publish(next);
    }

    @SuppressWarnings("unchecked")
    @Override
    public T take(final long sequence)
    {
        int index = index(sequence);

        T t = (T) buffer[index];
        buffer[index] = null;

        return t;
    }

    @Override
    public EventAccessor<T> get(final long sequence)
    {
        return this;
    }

    public BatchEventProcessor<EventAccessor<T>> createHandler(final EventHandler<T> handler)
    {
        BatchEventProcessor<EventAccessor<T>> processor =
                new BatchEventProcessorBuilder().build(
                        this,
                        sequencer.newBarrier(),
                        new AccessorEventHandler<>(handler));
        sequencer.addGatingSequences(processor.getSequence());

        return processor;
    }
}
