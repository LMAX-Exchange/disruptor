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
public final class MultiThreadedClaimStrategy extends AbstractMultithreadedClaimStrategy
    implements ClaimStrategy
{
    private static final int RETRIES = 1000;

    private final AtomicLongArray pendingPublication;
    private final int pendingMask;

    /**
     * Construct a new multi-threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     * @param pendingBufferSize number of item that can be pending for serialisation
     */
    public MultiThreadedClaimStrategy(final int bufferSize, final int pendingBufferSize)
    {
        super(bufferSize);
        
        if (Integer.bitCount(pendingBufferSize) != 1)
        {
            throw new IllegalArgumentException("pendingBufferSize must be a power of 2, was: " + pendingBufferSize);
        }

        this.pendingPublication = new AtomicLongArray(pendingBufferSize);
        this.pendingMask = pendingBufferSize - 1;
    }

    /**
     * Construct a new multi-threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     */
    public MultiThreadedClaimStrategy(final int bufferSize)
    {
        this(bufferSize, 1024);
    }

    @Override
    public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
    {
        int counter = RETRIES;
        while (sequence - cursor.get() > pendingPublication.length())
        {
            if (--counter == 0)
            {
                Thread.yield();
                counter = RETRIES;
            }
        }

        long expectedSequence = sequence - batchSize;
        for (long pendingSequence = expectedSequence + 1; pendingSequence < sequence; pendingSequence++)
        {
            pendingPublication.lazySet((int) pendingSequence & pendingMask, pendingSequence);
        }
        pendingPublication.set((int) sequence & pendingMask, sequence);

        long cursorSequence = cursor.get();
        if (cursorSequence >= sequence)
        {
            return;
        }

        expectedSequence = Math.max(expectedSequence, cursorSequence);
        long nextSequence = expectedSequence + 1;
        while (cursor.compareAndSet(expectedSequence, nextSequence))
        {
            expectedSequence = nextSequence;
            nextSequence++;
            if (pendingPublication.get((int) nextSequence & pendingMask) != nextSequence)
            {
                break;
            }
        }
    }
}
