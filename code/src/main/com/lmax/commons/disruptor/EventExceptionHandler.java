package com.lmax.commons.disruptor;

public interface EventExceptionHandler
{
    void handle(Exception ex, Entry currentEntry);
}
