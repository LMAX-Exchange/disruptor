package com.lmax.disruptor;

public interface CommitCallback
{
    public void commit(long sequence);
}
