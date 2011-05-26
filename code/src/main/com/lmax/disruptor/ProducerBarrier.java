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
    T nextEntry();

    /**
     * Commit an entry back to the {@link RingBuffer} to make it visible to {@link Consumer}s
     * @param entry to be committed back to the {@link RingBuffer}
     */
    void commit(T entry);

    /**
     * Claim a specific sequence in the {@link RingBuffer} when only one producer is involved.
     *
     * @param sequence to be claimed.
     * @return the claimed {@link Entry}
     */
    T claimEntry(long sequence);

    /**
     * Commit an entry back to the {@link RingBuffer} to make it visible to {@link Consumer}s.
     * Only use this method when forcing a sequence and you are sure only one producer exists.
     * This will cause the {@link RingBuffer} to advance the {@link RingBuffer#getCursor()} to this sequence.
     *
     * @param entry to be committed back to the {@link RingBuffer}
     */
    void forceCommit(T entry);
}
