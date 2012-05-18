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


import java.util.concurrent.atomic.AtomicLongArray;


/**
 * Strategy to be used when there are multiple publisher threads claiming sequences.
 *
 * This strategy is reasonably forgiving when the multiple publisher threads are highly contended or working in an
 * environment where there is insufficient CPUs to handle multiple publisher threads.  It requires 2 CAS operations
 * for a single publisher, compared to the {@link MultiThreadedLowContentionClaimStrategy} strategy which needs only a single
 * CAS and a lazySet per publication.
 */
public final class MultiThreadedClaimStrategyV3 extends AbstractMultithreadedClaimStrategy
    implements ClaimStrategy
{
    private final AtomicLongArray availableBuffer;
    private final int indexMask;

    /**
     * Construct a new multi-threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     * @param pendingBufferSize number of item that can be pending for serialisation
     */
    public MultiThreadedClaimStrategyV3(final int bufferSize)
    {
        super(bufferSize);
        availableBuffer = new AtomicLongArray(bufferSize);
        indexMask = bufferSize - 1;
        availableBuffer.set(0, -1);
    }

    @Override
    public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
    {
        int index = ((int) sequence) & indexMask;
        availableBuffer.lazySet(index, sequence);
        
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
    public boolean isAvailable(long sequence)
    {
        int index = ((int) sequence) & indexMask;
        return availableBuffer.get(index) == sequence;
    }
}
