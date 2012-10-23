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

import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
class MultiProducerSequencer extends AbstractSequencer
{
    private final int bufferSize;
    @SuppressWarnings("unused")
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
    private final Sequence wrapPointCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    /**
     * Construct a Sequencer with the selected wait strategy and buffer size.
     *
     * @param bufferSize the size of the buffer that this will sequence over.
     * @param waitStrategy for those waiting on sequences.
     */
    public MultiProducerSequencer(int bufferSize, final WaitStrategy waitStrategy)
    {
        this.bufferSize = bufferSize;
        this.waitStrategy = waitStrategy;
    }

    @Override
    public int getBufferSize()
    {
        return bufferSize;
    }
    
    Sequence getCursorSequence()
    {
        return cursor;
    }

    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        final long desiredSequence = cursor.get() + requiredCapacity;
        if (desiredSequence > wrapPointCache.get())
        {
            long wrapPoint = getMinimumSequence(gatingSequences, cursor.get()) + bufferSize;
            wrapPointCache.set(wrapPoint);
        
            if (desiredSequence > wrapPoint)
            {
                return false;
            }
        }
        
        return true;
    }

    @Override
    public long next()
    {
        long current;
        long next;

        do
        {
            current = cursor.get();
            next = current + 1;

            if (next > wrapPointCache.get())
            {
                long wrapPoint = getMinimumSequence(gatingSequences, cursor.get()) + bufferSize;
                wrapPointCache.set(wrapPoint);

                if (next > wrapPoint)
                {
                    LockSupport.parkNanos(1); // TODO, should we spin based on the wait strategy?
                    continue;
                }
            }

            else if (cursor.compareAndSet(current, next))
            {
                break;
            }
        }
        while (true);

        return next;
    }

    @Override
    public long tryNext() throws InsufficientCapacityException
    {
        long current;
        long next;

        do
        {
            current = cursor.get();
            next = current + 1;

            if (next > wrapPointCache.get())
            {
                long wrapPoint = getMinimumSequence(gatingSequences, cursor.get()) + bufferSize;
                wrapPointCache.set(wrapPoint);

                if (next > wrapPoint)
                {
                    throw InsufficientCapacityException.INSTANCE;
                }
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;
    }

    @Override
    public long claim(final long sequence)
    {
        cursor.set(sequence);
        waitForFreeSlotAt(sequence, gatingSequences);

        return sequence;
    }

    @Override
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences, cursor.get());
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }
    
    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences)
    {
        final long wrapPoint = sequence - bufferSize;
        if (wrapPoint > wrapPointCache.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences, cursor.get())))
            {
                LockSupport.parkNanos(1L);
            }

            wrapPointCache.set(minSequence);
        }
    }
}
