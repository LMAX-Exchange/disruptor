package com.lmax.commons.disruptor;



public abstract class AbstractSlotClaimer<T extends Entry>
    implements SlotClaimer<T>
{
    private final int bufferReserveThreshold;
    private final RingBuffer<? extends T> ringBuffer;
    private final EventConsumer[] gatingEventConsumers;

    public AbstractSlotClaimer(final int bufferReserveThreshold,
                               final RingBuffer<? extends T> ringBuffer,
                               final EventConsumer... gatingEventConsumers)
    {
        if (null == ringBuffer)
        {
            throw new NullPointerException();
        }

        if (gatingEventConsumers.length == 0)
        {
            throw new IllegalArgumentException();
        }

        this.bufferReserveThreshold = bufferReserveThreshold;
        this.ringBuffer = ringBuffer;
        this.gatingEventConsumers = gatingEventConsumers;
    }

    public abstract T claimNext();

    @Override
    public RingBuffer<? extends T> getRingBuffer()
    {
        return ringBuffer;
    }

    @Override
    public long getConsumedEventSequence()
    {
        long minimum = ringBuffer.getCursor();

        for (EventConsumer consumer : gatingEventConsumers)
        {
            long sequence = consumer.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }

    protected int getBufferReserveThreshold()
    {
        return bufferReserveThreshold;
    }
}
