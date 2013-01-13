package com.lmax.disruptor;

public interface MemoryAllocator
{
    /**
     * Allocate memory to back the OffHeapRingBuffer.
     * 
     * @param size the number of chunks to be held in this {@link Memory} instance
     * @param chunkSize the size of the individual chunks
     * @return the {@link Memory} instance
     */
    Memory allocate(int size, int chunkSize);
}
