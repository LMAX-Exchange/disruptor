package com.lmax.disruptor;

/**
 * SlotClaimer that uses a busy spin strategy when trying to claim a slot in the {@link RingBuffer}
 *
 * This strategy is a good option when low-latency is the highest priority.
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public final class BusySpinSlotClaimer<T extends Entry>
    extends AbstractSlotClaimer<T>
{
    public BusySpinSlotClaimer(final int bufferReserveThreshold,
                               final RingBuffer<? extends T> ringBuffer,
                               final EventConsumer... gatingEventConsumers)
    {
        super(bufferReserveThreshold, ringBuffer, gatingEventConsumers);
    }

    @Override
    public T claimNext()
    {
        final RingBuffer<? extends T> ringBuffer = getRingBuffer();

        final long threshold = ringBuffer.getCapacity() - getBufferReserve();
        while (ringBuffer.getCursor() - getConsumedEventSequence() >= threshold)
        {
            // busy spin
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
        	// busy spin
        }

        return ringBuffer.claimSequence(sequence);
    }
}
