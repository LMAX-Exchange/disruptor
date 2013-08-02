package com.lmax.disruptor;

public interface EventReleasingWorkHandler<T> extends WorkHandler<T>
{
    void setEventReleaser(EventReleaser eventReleaser);
}
