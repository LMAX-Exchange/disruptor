/*
 * Copyright 2012 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;

/**
 * A Publisher optimised for use from multiple threads.<p/>
 *
 * Suitable for use for publishing from multiple threads.
 */
class MultiProducerPublisher implements Publisher
{
    private static final Unsafe UNSAFE = Util.getUnsafe();
    private static final long base = UNSAFE.arrayBaseOffset(int[].class);
    private static final long scale = UNSAFE.arrayIndexScale(int[].class);
    
    private final WaitStrategy waitStrategy;
    // int[] tracks the state of each ringbuffer slot
    private final int[] availableBuffer;
    private final int indexMask;
    private final int indexShift;

    public MultiProducerPublisher(int bufferSize, WaitStrategy waitStrategy)
    {
        this.waitStrategy = waitStrategy;
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);

        initialiseAvailableBuffer();
    }

    private void initialiseAvailableBuffer()
    {
        for (int i = availableBuffer.length - 1; i != 0; i--)
        {
            setAvailableBufferValue(i, -1);
        }

        setAvailableBufferValue(0, -1);
    }

    @Override
    public void publish(final long sequence)
    {
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    private void setAvailable(final long sequence)
    {
        setAvailableBufferValue(calculateIndex(sequence), calculateAvailabilityFlag(sequence));
    }
    
    private void setAvailableBufferValue(int index, int flag)
    {
        long bufferAddress = (index * scale) + base;
        UNSAFE.putOrderedInt(availableBuffer, bufferAddress, flag);
    }

    @Override
    public void ensureAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * scale) + base;
        
        while (UNSAFE.getIntVolatile(availableBuffer, bufferAddress) != flag)
        {
            assert UNSAFE.getIntVolatile(availableBuffer, bufferAddress) <= flag;
            // spin
        }
    }

    @Override
    public boolean isAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * scale) + base;
        return UNSAFE.getIntVolatile(availableBuffer, bufferAddress) == flag;
    }
    
    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift);
    }

    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
