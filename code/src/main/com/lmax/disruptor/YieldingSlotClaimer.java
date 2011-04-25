package com.lmax.disruptor;

/**
 * SlotClaimer that uses a thread yielding strategy when trying to claim a slot in the {@link RingBuffer}
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public final class YieldingSlotClaimer<T extends Entry>
    extends AbstractSlotClaimer<T>
{

    public YieldingSlotClaimer(final int bufferReserveThreshold,
                               final RingBuffer<? extends T> ringBuffer,
                               final EventConsumer... gatingEventConsumers)
    {
        super(bufferReserveThreshold, ringBuffer, gatingEventConsumers);
    }

    @Override
    public T claimNext()
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserveThreshold();
        while (ringBuffer.getCursor() - getConsumedEventSequence() >= threshold)
        {
            Thread.yield();
        }

        return ringBuffer.claimNext();
    }

    @Override
    public T claimSequence(long sequence)
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserveThreshold();
        while (sequence - getConsumedEventSequence() >= threshold)
        {
            Thread.yield();
        }

        return ringBuffer.claimSequence(sequence);
    }
}
