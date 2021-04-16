package com.lmax.disruptor;

public class RewindableException extends RuntimeException
{
    public RewindableException(final Throwable cause)
    {
        super("REWINDING BATCH", cause);
    }
}
