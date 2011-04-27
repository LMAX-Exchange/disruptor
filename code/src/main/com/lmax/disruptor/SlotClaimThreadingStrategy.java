package com.lmax.disruptor;

/**
 * Indicates the threading policy to be applied for claiming slots by producers to the {@link RingBuffer}
 */
public enum SlotClaimThreadingStrategy
{
    MULTI_THREADED
    {
        @Override
        public SlotClaimStrategy newInstance()
        {
            return new MultiThreadedSlotClaimStrategy();
        }
    },

    SINGLE_THREADED
    {
        @Override
        public SlotClaimStrategy newInstance()
        {
            return new SingleThreadedSlotClaimStrategy();
        }
    };

    /**
     * Used by the {@link RingBuffer} as a polymorphic constructor.
     *
     * @return a new instance of the SlotClaimStrategy
     */
    abstract SlotClaimStrategy newInstance();
}
