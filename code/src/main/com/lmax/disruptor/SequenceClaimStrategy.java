package com.lmax.disruptor;

public interface SequenceClaimStrategy
{
    long getAndIncrement();
    void setSequence(long sequence);
}
