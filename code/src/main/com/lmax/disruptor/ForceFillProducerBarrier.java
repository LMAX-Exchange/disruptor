package com.lmax.disruptor;

/**
 * Abstraction for claiming {@link Entry}s in a {@link RingBuffer} while tracking dependent {@link Consumer}s.
 *
 * This barrier can be used to pre-fill a {@link RingBuffer} but only when no other producers are active.
 *
 * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
 */
public interface ForceFillProducerBarrier<T extends Entry>
{
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
    void commit(T entry);

    /**
     * Delegate a call to the {@link RingBuffer#getCursor()}
     *
     * @return value of the cursor for entries that have been published.
     */
    long getCursor();
}