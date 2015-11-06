package com.lmax.disruptor.primitive;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.Sequencer;

public class LongRingBuffer
{
    private final Sequencer sequencer;
    private final long[] buffer;
    private final int mask;

    public LongRingBuffer(final Sequencer sequencer)
    {
        this.sequencer = sequencer;
        this.buffer = new long[sequencer.getBufferSize()];
        this.mask = sequencer.getBufferSize() - 1;
    }

    private int index(final long sequence)
    {
        return (int) sequence & mask;
    }

    public void put(final long e)
    {
        final long next = sequencer.next();
        buffer[index(next)] = e;
        sequencer.publish(next);
    }

    public interface LongHandler
    {
        void onEvent(long value, long sequence, boolean endOfBatch);
    }

    private class LongEvent implements DataProvider<LongEvent>
    {
        private long sequence;

        public long get()
        {
            return buffer[index(sequence)];
        }

        @Override
        public LongEvent get(final long sequence)
        {
            this.sequence = sequence;
            return this;
        }
    }

    public BatchEventProcessor<LongEvent> createProcessor(final LongHandler handler)
    {
        return new BatchEventProcessor<LongEvent>(
            new LongEvent(),
            sequencer.newBarrier(),
            new EventHandler<LongEvent>()
            {
                @Override
                public void onEvent(final LongEvent event, final long sequence, final boolean endOfBatch)
                    throws Exception
                {
                    handler.onEvent(event.get(), sequence, endOfBatch);
                }
            });
    }
}
