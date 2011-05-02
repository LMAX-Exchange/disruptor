package com.lmax.disruptor;

/**
 * Abstraction for claiming {@link Entry}s in a {@link RingBuffer} while tracking dependent {@link EntryConsumer}s
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public interface Claimer<T extends Entry>
{
    /**
     * Claim the next {@link Entry} in sequence for a producer on the {@link RingBuffer}
     *
     * @return the claimed {@link Entry}
     */
    T claimNext();

    /**
     * Claim a specific sequence in the {@link RingBuffer} when only one producer is involved.
     *
     * @param sequence to be claimed.
     * @return the claimed {@link Entry}
     */
    T claimSequence(long sequence);

    /**
     * Get the sequence up to which the {@link EntryConsumer}s have consumed from the {@link RingBuffer}
     *
     * @return the consumed to sequence
     */
    long getConsumedEntrySequence();

    /**
     * Get the underlying {@link RingBuffer} in which {@link Entry}s are being claimed.
     *
     * @return the {@link RingBuffer}
     */
    RingBuffer<? extends T> getRingBuffer();

    /**
     * The number of slots in the buffer that have been reserved to prevent wrapping.
     *
     * @return size of the reserve.
     */
    int getBufferReserve();
}
