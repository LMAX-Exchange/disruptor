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

import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s.<p/>
 *
 * Generally not safe for use from multiple threads as it does not implement any barriers.
 */
public final class SingleProducerSequencer extends AbstractSequencer
{
    @SuppressWarnings("unused")
    private static class Padding
    {
        /** Set to -1 as sequence starting point */
        public long nextValue = Sequence.INITIAL_VALUE, cachedValue = Sequence.INITIAL_VALUE, p2, p3, p4, p5, p6, p7;
    }
    
    private final Padding pad = new Padding();

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public SingleProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
        super(bufferSize, waitStrategy);
    }
    
    long getNextValue()
    {
        return pad.nextValue;
    }

    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        long nextValue = pad.nextValue;
        
        long wrapPoint = (nextValue + requiredCapacity) - bufferSize;
        long cachedGatingSequence = pad.cachedValue;
        
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            long minSequence = Util.getMinimumSequence(gatingSequences, nextValue);
            pad.cachedValue = minSequence;
        
            if (wrapPoint > minSequence)
            {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public long next()
    {
        return next(1);
    }

    @Override
    public long next(int n)
    {
        long nextValue = pad.nextValue;
        
        long nextSequence = nextValue + n;
        long wrapPoint = nextSequence - bufferSize;
        long cachedGatingSequence = pad.cachedValue;
        
        if (wrapPoint > cachedGatingSequence || cachedGatingSequence > nextValue)
        {
            long minSequence;
            while (wrapPoint > (minSequence = Util.getMinimumSequence(gatingSequences, nextValue)))
            {
                LockSupport.parkNanos(1L); // TODO: Use waitStrategy to spin?
            }
        
            pad.cachedValue = minSequence;
        }
        
        pad.nextValue = nextSequence;
        
        return nextSequence;
    }

    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        return tryNext(1);
    }

    @Override
    public long tryNext(int n) throws InsufficientCapacityException
    {
        if (!hasAvailableCapacity(n))
        {
            throw InsufficientCapacityException.INSTANCE;
        }

        long nextSequence = pad.nextValue += n;
        
        return nextSequence;
    }

    @Override
    public long remainingCapacity(Sequence[] gatingSequences)
    {
        long nextValue = pad.nextValue;
        
        long consumed = Util.getMinimumSequence(gatingSequences, nextValue);
        long produced = nextValue;
        return getBufferSize() - (produced - consumed);
    }
    
    @Override
    public void claim(long sequence)
    {
        pad.nextValue = sequence;
    }

    @Override
    public void publish(long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }
    
    @Override
    public void publish(long lo, long hi)
    {
        publish(hi);
    }

    @Override
    public void ensureAvailable(long sequence)
    {
    }

    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    Sequence getCursorSequence()
    {
        return cursor;
    }
}
