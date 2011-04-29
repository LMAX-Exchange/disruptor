package com.lmax.disruptor;

/**
 * EntryClaimer that uses a thread yielding strategy when trying to claim a {@link Entry} in the {@link RingBuffer}.
 * <p>
 * This strategy is a good compromise between performance and CPU resource.
 * <p>
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public final class YieldingEntryClaimer<T extends Entry>
    extends AbstractEntryClaimer<T>
{

    public YieldingEntryClaimer(final int bufferReserve,
                                final RingBuffer<? extends T> ringBuffer,
                                final EntryConsumer... gatingEntryConsumers)
    {
        super(bufferReserve, ringBuffer, gatingEntryConsumers);
    }

    @Override
    public T claimNext()
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
        while (ringBuffer.getCursor() - getConsumedEntrySequence() >= threshold)
        {
            Thread.yield();
        }

        return ringBuffer.claimNext();
    }

    @Override
    public T claimSequence(final long sequence)
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
        while (sequence - getConsumedEntrySequence() >= threshold)
        {
            Thread.yield();
        }

        return ringBuffer.claimSequence(sequence);
    }
}
