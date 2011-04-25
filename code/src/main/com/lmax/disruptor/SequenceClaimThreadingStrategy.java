package com.lmax.disruptor;

public enum SequenceClaimThreadingStrategy
{
    MULTI_THREADED
    {
        @Override
        public SequenceClaimStrategy newInstance()
        {
            return new MultiThreadedSequenceClaimStrategy();
        }
    },

    SINGLE_THREADED
    {
        @Override
        public SequenceClaimStrategy newInstance()
        {
            return new SingleThreadedSequenceClaimStrategy();
        }
    };

    public abstract SequenceClaimStrategy newInstance();
}
