package com.lmax.commons.disruptor;

public interface CommitCallback
{
    public void commit(long sequence);
}
