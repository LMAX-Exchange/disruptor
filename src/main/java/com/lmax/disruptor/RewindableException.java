package com.lmax.disruptor;

public class RewindableException extends RuntimeException
{
    public RewindableException(Throwable cause)
    {
        super("REWINDING BATCH", cause);
    }
}
