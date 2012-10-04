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

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.PaddedLong;
import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
class SingleProducerSequencer implements Sequencer
{
    /** Set to -1 as sequence starting point */
    private final PaddedLong minGatingSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
//    private final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private long cursor = Sequencer.INITIAL_CURSOR_VALUE;
    private Sequence[] gatingSequences;

    private final WaitStrategy waitStrategy;

    private final int bufferSize;

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    @Override
    public void setGatingSequences(final Sequence... sequences)
    {
        this.gatingSequences = sequences;
    }

    @Override
    public int getBufferSize()
    {
        return bufferSize;
    }

    @Override
    public long getCursor()
    {
//        return cursor.get();
        return cursor;
    }

    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(requiredCapacity, gatingSequences);
    }

    @Override
    public long next()
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        long nextSequence = cursor + 1;
        waitForFreeSlotAt(nextSequence, gatingSequences);
        cursor = nextSequence;
        
        return nextSequence;
    }

    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        if (!hasAvailableCapacity(1, gatingSequences))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = cursor + 1;
        cursor = nextSequence;
        
        return nextSequence;
    }

    @Override
    public long claim(final long sequence)
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        cursor = sequence;
        waitForFreeSlotAt(sequence, gatingSequences);

        return sequence;
    }

    @Override
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences);
        long produced = cursor;
        return getBufferSize() - (produced - consumed);
    }
    
    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences)
    {
        final long wrapPoint = sequence - bufferSize;
        if (wrapPoint > minGatingSequence.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }

            minGatingSequence.set(minSequence);
        }
    }

    private boolean hasAvailableCapacity(final int requiredCapacity, final Sequence[] dependentSequences)
    {
        final long wrapPoint = (cursor + requiredCapacity) - bufferSize;
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
}
