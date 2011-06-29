package com.lmax.disruptor;

/**
 * Called by the {@link RingBuffer} to pre-populate all the {@link AbstractEntry}s to fill the RingBuffer.
 * 
 * @param <T> AbstractEntry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public interface EntryFactory<T extends AbstractEntry>
{
    T create();
}