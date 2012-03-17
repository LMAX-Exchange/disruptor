package com.lmax.disruptor;

@SuppressWarnings("serial")
public class InsufficientCapacityException extends Exception
{
    public static final InsufficientCapacityException INSTANCE = new InsufficientCapacityException();
    
    private InsufficientCapacityException()
    {
        // Singleton
    }
    
    @Override
    public synchronized Throwable fillInStackTrace()
    {
        return this;
    }
}
