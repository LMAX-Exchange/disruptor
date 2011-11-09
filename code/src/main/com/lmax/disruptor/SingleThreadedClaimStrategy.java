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

import com.lmax.disruptor.util.PaddedLong;

import java.util.concurrent.locks.LockSupport;

import static com.lmax.disruptor.util.Util.getMinimumSequence;

/**
 * Optimised strategy can be used when there is a single publisher thread claiming sequences.
 *
 * This strategy must <b>not</b> be used when multiple threads are used for publishing concurrently on the same {@link Sequencer}
 */
public final class SingleThreadedClaimStrategy
    implements ClaimStrategy
{
    private final int bufferSize;
    private final PaddedLong minGatingSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final PaddedLong claimSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);

    /**
     * Construct a new single threaded publisher {@link ClaimStrategy} for a given buffer size.
     *
     * @param bufferSize for the underlying data structure.
     */
    public SingleThreadedClaimStrategy(final int bufferSize)
    {
        this.bufferSize = bufferSize;
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
        long nextSequence = claimSequence.get() + 1L;
        claimSequence.set(nextSequence);
        waitForFreeSlotAt(nextSequence, dependentSequences);

        return nextSequence;
    }

    @Override
    public long incrementAndGet(final int delta, final Sequence[] dependentSequences)
    {
        long nextSequence = claimSequence.get() + delta;
        claimSequence.set(nextSequence);
        waitForFreeSlotAt(nextSequence, dependentSequences);

        return nextSequence;
    }

    @Override
    public void setSequence(final long sequence, final Sequence[] dependentSequences)
    {
        claimSequence.set(sequence);
        waitForFreeSlotAt(sequence, dependentSequences);
    }

    @Override
    public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
    {
        cursor.set(sequence);
    }

    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences)
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
