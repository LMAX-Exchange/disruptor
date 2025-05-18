package com.lmax.disruptor;

/**
 * Wait strategies may throw this exception to inform producer thread
 * that a message has not been put into ring buffer within the specified timeout.
 **/
public final class RuntimeTimeoutException extends RuntimeException
{
    public RuntimeTimeoutException(final String message)
    {
        super(message);
    }
}
