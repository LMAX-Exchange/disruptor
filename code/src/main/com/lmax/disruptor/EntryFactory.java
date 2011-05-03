package com.lmax.disruptor;

/**
 * Called by the {@link RingBuffer} to pre-populate all the {@link Entry}s to fill the RingBuffer.
 * 
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EntryFactory<T>
{
    T create();
}