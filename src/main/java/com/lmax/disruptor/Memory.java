package com.lmax.disruptor;

public interface Memory
{
    byte getByte(int index, int offset);
    void putByte(int index, int offset, byte value);
    
    short getShort(int index, int offset);
    void putShort(int index, int offset, short value);
    
    char getChar(int index, int offset);
    void putChar(int index, int offset, char value);
    
    int getInt(int index, int offset);
    void putInt(int index, int lengthOffset, int value);
    
    long getLong(int index, int offset);
    long getVolatileLong(int index, int offset);
    void putLong(int index, int offset, long value);
    void putOrderedLong(int index, int offset, long value);

    float getFloat(int index, int offset);
    void putFloat(int index, int offset, float value);
    
    double getDouble(int index, int offset);
    void putDouble(int index, int offset, double value);
    
    byte[] getBytes(int index, int offset, int length);
    int getBytes(int index, int offset, int length, byte[] data);
    int putBytes(int index, int offset, byte[] value, int arrayOffset, int length);
    
    int getSize();
    int getChunkSize();
    int indexOf(long next);

}
