package com.lmax.disruptor;

/**
 * Abstraction for claiming {@link Entry}s in a {@link RingBuffer} while tracking dependent {@link Consumer}s
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public interface ProducerBarrier<T extends Entry>
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
}
