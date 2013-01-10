package com.lmax.disruptor;

interface SimpleData extends RingBufferEntry
{
    int getLength();
    void setLength(int value);
    
    long getPreviousSequence();
    void setPreviousSequence(long value);
    
    int getDataLength();
    void setDataLength(int value);

    byte[] getData();
    int setData(byte[] value, int offset, int length);
}