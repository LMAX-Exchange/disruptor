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

import static com.lmax.disruptor.util.Util.getMinimumSequence;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.MutableLong;
import com.lmax.disruptor.util.PaddedAtomicLong;

/**
 * Strategy to be used when there are multiple publisher threads claiming sequences.
 *
 * This strategy is reasonably forgiving when the multiple publisher threads are highly contended or working in an
 * environment where there is insufficient CPUs to handle multiple publisher threads.  It requires 2 CAS operations
 * for a single publisher, compared to the {@link MultiThreadedLowContentionClaimStrategy} strategy which needs only a single
 * CAS and a lazySet per publication.
 */
public final class MultiThreadedClaimStrategy
    implements ClaimStrategy
{
    private static final int RETRIES = 1000;

    private final int bufferSize;
    private final PaddedAtomicLong claimSequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final AtomicLongArray pendingPublication;
    private final int pendingMask;

    private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
    {
        @Override
        protected MutableLong initialValue()
        {
            return new MutableLong(Sequencer.INITIAL_CURSOR_VALUE);
        }
    };

    /**
     * Construct a new multi-threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     * @param pendingBufferSize number of item that can be pending for serialisation
     */
    public MultiThreadedClaimStrategy(final int bufferSize, final int pendingBufferSize)
    {
        if (Integer.bitCount(pendingBufferSize) != 1)
        {
            throw new IllegalArgumentException("pendingBufferSize must be a power of 2, was: " + pendingBufferSize);
        }

        this.bufferSize = bufferSize;
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
    public int getBufferSize()
    {
        return bufferSize;
    }

    @Override
    public long getSequence()
    {
        return claimSequence.get();
    }

    @Override
    public boolean hasAvailableCapacity(final int availableCapacity, final Sequence[] dependentSequences)
    {
        final long wrapPoint = (claimSequence.get() + availableCapacity) - bufferSize;
        final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence = getMinimumSequence(dependentSequences);
            minGatingSequence.set(minSequence);

            if (wrapPoint > minSequence)
            {
                return false;
            }
        }

        return true;
    }

    @Override
    public long incrementAndGet(final Sequence[] dependentSequences)
    {
        final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
        waitForCapacity(dependentSequences, minGatingSequence);

        final long nextSequence = claimSequence.incrementAndGet();
        waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequence);

        return nextSequence;
    }

    @Override
    public long incrementAndGet(final int delta, final Sequence[] dependentSequences)
    {
        final long nextSequence = claimSequence.addAndGet(delta);
        waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequenceThreadLocal.get());

        return nextSequence;
    }

    @Override
    public void setSequence(final long sequence, final Sequence[] dependentSequences)
    {
        claimSequence.lazySet(sequence);
        waitForFreeSlotAt(sequence, dependentSequences, minGatingSequenceThreadLocal.get());
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
        for (long pendingSequence = expectedSequence + 1; pendingSequence <= sequence; pendingSequence++)
        {
            pendingPublication.set((int) pendingSequence & pendingMask, pendingSequence);
        }

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

    private void waitForCapacity(final Sequence[] dependentSequences, final MutableLong minGatingSequence)
    {
        final long wrapPoint = (claimSequence.get() + 1L) - bufferSize;
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L);
            }

            minGatingSequence.set(minSequence);
        }
    }

    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences, final MutableLong minGatingSequence)
    {
        final long wrapPoint = sequence - bufferSize;
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L);
            }

            minGatingSequence.set(minSequence);
        }
    }
}
