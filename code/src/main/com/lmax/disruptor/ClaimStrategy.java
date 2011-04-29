package com.lmax.disruptor;

/**
 * Implementations of this strategy can handled the necessary threading requirements
 * for claiming {@link Entry}s in the {@link RingBuffer}.
 *
 * The {@link Entry} index is a the sequence value mod the {@link RingBuffer} capacity.
 */
public interface ClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the {@link Entry} index to be used for the producer.
     */
    long getAndIncrement();

    /**
     * Set the current sequence value for claiming {@link Entry} in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(long sequence);

    /**
     * Indicates the threading policy to be applied for claiming {@link Entry}s by producers to the {@link com.lmax.disruptor.RingBuffer}
     */
    enum Option
    {
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new MultiThreadedClaimStrategy();
            }
        },

        SINGLE_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new SingleThreadedClaimStrategy();
            }
        };

        /**
         * Used by the {@link com.lmax.disruptor.RingBuffer} as a polymorphic constructor.
         *
         * @return a new instance of the ClaimStrategy
         */
        abstract ClaimStrategy newInstance();
    }
}
