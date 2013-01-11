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
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.<p/>
 *
 * Generally not safe for use from multiple threads as it does not implement any barriers.
 */
class SingleProducerSequencer implements Sequencer
{
    /** Set to -1 as sequence starting point */
    private final PaddedLong gatingSequenceCache = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
    private long nextValue = Sequencer.INITIAL_CURSOR_VALUE;
    @SuppressWarnings("unused")
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
    public int getBufferSize()
    {
        return bufferSize;
    }
    
    long getNextValue()
    {
        return nextValue;
    }

    @Override
    public boolean hasAvailableCapacity(Sequence[] gatingSequences, final int requiredCapacity)
    {
        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();
        
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            long minSequence = getMinimumSequence(gatingSequences, nextValue);
            gatingSequenceCache.set(minSequence);
        
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public long next(Sequence[] gatingSequences)
    {
        long nextSequence = nextValue + 1;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = gatingSequenceCache.get();
        
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(gatingSequences, nextValue)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
        
            gatingSequenceCache.set(minSequence);
        }
        
        nextValue = nextSequence;
        
        return nextSequence;
    }

    @Override
    public long tryNext(Sequence[] gatingSequences) throws InsufficientCapacityException
    {
        if (!hasAvailableCapacity(gatingSequences, 1))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = ++nextValue;
        
        return nextSequence;
    }

    @Override
    public long remainingCapacity(Sequence[] gatingSequences)
    {
        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }
    
    @Override
    public void claim(long sequence)
    {
        nextValue = sequence;
    }
}
