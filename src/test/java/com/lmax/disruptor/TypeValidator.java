package com.lmax.disruptor;

import java.util.Random;

public interface TypeValidator
{
    void init(Random r, int numberOfPuts, int size, int chunkSize);
    boolean putAndGetAt(Memory memory, int i, long index, int offset);
    boolean validateGetAt(Memory memory, long index, int offset);
    int getTypeSize();
}
