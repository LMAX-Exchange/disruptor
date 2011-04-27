package com.lmax.disruptor;

public interface SlotClaimStrategy
{
    long getAndIncrement();
    void setSequence(long sequence);
}
