package com.lmax.disruptor;

public interface EventExceptionHandler
{
    void handle(Exception ex, Entry currentEntry);
}
