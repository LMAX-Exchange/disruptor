package com.lmax.disruptor;


public interface ProgressReportingEventHandler<T extends Entry> extends BatchEventHandler<T>
{
    void setProgressTracker(final BatchEventConsumer.ProgressTrackerCallback progressTrackerCallback);
}
