package com.lmax.disruptor;

/**
 * SlotClaimer that uses a thread yielding strategy when trying to claim a slot in the {@link RingBuffer}.
 * <p>
 * This strategy is a good compromise between performance and CPU resource.
 * <p>
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public final class YieldingSlotClaimer<T extends Entry>
    extends AbstractSlotClaimer<T>
{

    public YieldingSlotClaimer(final int bufferReserve,
                               final RingBuffer<? extends T> ringBuffer,
                               final EventConsumer... gatingEventConsumers)
    {
        super(bufferReserve, ringBuffer, gatingEventConsumers);
    }

    @Override
    public T claimNext()
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
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

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
        while (sequence - getConsumedEventSequence() >= threshold)
        {
            Thread.yield();
        }

        return ringBuffer.claimSequence(sequence);
    }
}
