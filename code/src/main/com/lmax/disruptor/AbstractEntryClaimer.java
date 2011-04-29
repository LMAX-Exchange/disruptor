package com.lmax.disruptor;

/**
 * Convenience class holding common functionality for {@link EntryClaimer}s.
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public abstract class AbstractEntryClaimer<T extends Entry>
    implements EntryClaimer<T>
{
    private final int bufferReserve;
    private final RingBuffer<? extends T> ringBuffer;
    private final EntryConsumer[] gatingEntryConsumers;

    public AbstractEntryClaimer(final int bufferReserve,
                                final RingBuffer<? extends T> ringBuffer,
                                final EntryConsumer... gatingEntryConsumers)
    {
        if (null == ringBuffer)
        {
            throw new NullPointerException();
        }

        if (gatingEntryConsumers.length == 0)
        {
            throw new IllegalArgumentException();
        }

        this.bufferReserve = bufferReserve;
        this.ringBuffer = ringBuffer;
        this.gatingEntryConsumers = gatingEntryConsumers;
    }

    public abstract T claimNext();

    @Override
    public RingBuffer<? extends T> getRingBuffer()
    {
        return ringBuffer;
    }

    @Override
    public long getConsumedEntrySequence()
    {
        long minimum = ringBuffer.getCursor();

        for (EntryConsumer consumer : gatingEntryConsumers)
        {
            long sequence = consumer.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }

    protected int getBufferReserve()
    {
        return bufferReserve;
    }
}
