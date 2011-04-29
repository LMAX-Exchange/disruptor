package com.lmax.disruptor;

/**
 * EntryClaimer that uses a busy spin strategy when trying to claim a {@link Entry} in the {@link RingBuffer}
 *
 * This strategy is a good option when low-latency is the highest priority.
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public final class BusySpinEntryClaimer<T extends Entry>
    extends AbstractEntryClaimer<T>
{
    public BusySpinEntryClaimer(final int bufferReserveThreshold,
                                final RingBuffer<? extends T> ringBuffer,
                                final EntryConsumer... gatingEntryConsumers)
    {
        super(bufferReserveThreshold, ringBuffer, gatingEntryConsumers);
    }

    @Override
    public T claimNext()
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
        while (ringBuffer.getCursor() - getConsumedEntrySequence() >= threshold)
        {
            // busy spin
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
        	// busy spin
        }

        return ringBuffer.claimSequence(sequence);
    }
}
