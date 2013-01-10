package com.lmax.disruptor;

public interface Memory
{
    int getInt(int index, int offset);
    void putInt(int index, int lengthOffset, int value);
    
    long getLong(int index, int offset);
    long getVolatileLong(int index, int offset);
    void putLong(int index, int offset, long value);
    void putOrderedLong(int index, int offset, long value);

    byte[] getBytes(int index, int offset, int length);
    int putBytes(int index, int offset, byte[] value, int arrayOffset, int length);

    int getSize();
    int getChunkSize();
    int indexOf(long next);
}
