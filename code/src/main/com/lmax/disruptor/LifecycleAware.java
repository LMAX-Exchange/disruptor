package com.lmax.disruptor;

/**
 * Implement this interface to be notified when a thread for the {@link BatchConsumer} starts and shuts down.
 */
public interface LifecycleAware
{
    /**
     * Called once on thread start before first entry is available.
     */
    void onStart();

    /**
     * Called once just before the thread is shutdown.
     */
    void onShutdown();
}
