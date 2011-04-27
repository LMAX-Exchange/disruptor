package com.lmax.disruptor;

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

    public abstract SlotClaimStrategy newInstance();
}
