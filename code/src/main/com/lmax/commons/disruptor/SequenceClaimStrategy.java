package com.lmax.commons.disruptor;

public interface SequenceClaimStrategy
{
    long getAndIncrement();
    void setSequence(long sequence);
}
