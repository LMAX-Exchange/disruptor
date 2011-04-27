package com.lmax.disruptor;

/**
 * Implementations of this strategy can handled the necessary threading requirements
 * for claiming slots in the {@link RingBuffer}.
 *
 * The slot index is a the sequence value mod the {@link RingBuffer} capacity.
 */
interface SlotClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the slot index to be used for the producer.
     */
    long getAndIncrement();

    /**
     * Set the current sequence value for claiming slots in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(long sequence);
}
