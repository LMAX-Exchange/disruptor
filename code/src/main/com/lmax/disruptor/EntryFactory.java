package com.lmax.disruptor;

/**
 * Called by the {@link RingBuffer} to pre-populate all the slots in the ring.
 * 
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EntryFactory<T>
{
    T create();
}