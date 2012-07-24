package com.lmax.disruptor;

public interface ReferencePublisher<E>
{
    /**
     * Puts the event onto the ring buffer, will block until space is available.
     * 
     * @param event to put into the ring buffer.
     */
    void put(E event);
    
    /**
     * Puts the event onto the ring buffer only if there is space available.
     * Return <code>false</code> if there was no space available.
     * 
     * @param event to put into the ring buffer.
     * @return indicates if there was space available.
     */
    boolean offer(E event);
}