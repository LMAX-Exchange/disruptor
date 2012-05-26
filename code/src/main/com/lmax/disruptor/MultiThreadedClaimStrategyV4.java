/*
 * Copyright 2011 LMAX Ltd.
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


import java.util.concurrent.atomic.AtomicIntegerArray;

import com.lmax.disruptor.util.Util;


/**
 * Strategy to be used when there are multiple publisher threads claiming sequences.
 */
public final class MultiThreadedClaimStrategyV4 extends AbstractMultithreadedClaimStrategy
    implements ClaimStrategy
{
    private final AtomicIntegerArray availableBuffer;
    private final int indexMask;
    private final int indexShift;

    /**
     * Construct a new multi-threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     * @param pendingBufferSize number of item that can be pending for serialisation
     */
    public MultiThreadedClaimStrategyV4(final int bufferSize)
    {
        super(bufferSize);
        availableBuffer = new AtomicIntegerArray(bufferSize);
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
        
        initialiseAvailableBuffer();
    }

    private void initialiseAvailableBuffer()
    {
        for (int i = availableBuffer.length() - 1; i != 0; i--)
        {
            availableBuffer.lazySet(i, -1);
        }
        
        availableBuffer.set(0, -1);
    }

    @Override
    public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        availableBuffer.lazySet(index, flag);
        
        long cursorSequnece;
        while ((cursorSequnece = cursor.get()) < sequence)
        {
            if (cursor.compareAndSet(cursorSequnece, sequence))
            {
                break;
            }
        }
    }
    
    @Override
    public void ensureAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        while (availableBuffer.get(index) != flag)
        {
            // spin
        }
    }

    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift) & 1;
    }

    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
