package com.lmax.commons.disruptor;


public interface ProgressReportingEventHandler<T extends Entry> extends EventHandler<T>
{
    void setProgressTracker(final BatchEventConsumer.ProgressTrackerCallback progressTrackerCallback);
}
