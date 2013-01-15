package com.lmax.disruptor;

public interface Memory
{
    byte getByte(long reference, int offset);
    void putByte(long reference, int offset, byte value);
    
    short getShort(long reference, int offset);
    void putShort(long reference, int offset, short value);
    
    char getChar(long reference, int offset);
    void putChar(long reference, int offset, char value);
    
    int getInt(long reference, int offset);
    void putInt(long reference, int lengthOffset, int value);
    
    long getLong(long reference, int offset);
    long getVolatileLong(long reference, int offset);
    void putLong(long reference, int offset, long value);
    void putOrderedLong(long reference, int offset, long value);

    float getFloat(long reference, int offset);
    void putFloat(long reference, int offset, float value);
    
    double getDouble(long reference, int offset);
    void putDouble(long reference, int offset, double value);
    
    byte[] getBytes(long reference, int offset, int length);
    int getBytes(long reference, int offset, int length, byte[] data);
    int putBytes(long reference, int offset, byte[] value, int arrayOffset, int length);
    
    int getEntryCount();
    int getEntrySize();
    long indexOf(long next);
}
