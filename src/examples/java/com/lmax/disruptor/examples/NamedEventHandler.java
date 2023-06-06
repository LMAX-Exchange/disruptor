package com.lmax.disruptor.examples;

import com.lmax.disruptor.EventHandler;

public class NamedEventHandler<T> implements EventHandler<T> {

    private String oldName;

    private final String name;

    public NamedEventHandler(final String name) {
        this.name = name;
    }

    @Override
    public void onEvent(final T event, final long sequence, final boolean endOfBatch) {
    }

    @Override
    public void onStart() {
        final Thread currentThread = Thread.currentThread();
        oldName = currentThread.getName();
        currentThread.setName(name);
    }

    @Override
    public void onShutdown() {
        Thread.currentThread().setName(oldName);
    }
}
