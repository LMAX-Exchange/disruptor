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

import sun.misc.Unsafe;

import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public class MultiProducerSequencer implements Sequencer
{
    private static final Unsafe UNSAFE = Util.getUnsafe();
    private static final long base = UNSAFE.arrayBaseOffset(int[].class);
    private static final long scale = UNSAFE.arrayIndexScale(int[].class);
    
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
    private final Sequence wrapPointCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final int[] availableBuffer;
    private final int bufferSize;
    private final int indexMask;
    private final int indexShift;
    
    private Sequence[] gatingSequences;

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
        availableBuffer = new int[bufferSize];
        indexMask = bufferSize - 1;
        indexShift = Util.log2(bufferSize);
        
        initialiseAvailableBuffer();
    }

    private void initialiseAvailableBuffer()
    {
        for (int i = availableBuffer.length - 1; i != 0; i--)
        {
            setAvailableBufferValue(i, 0);
        }
        
        setAvailableBufferValue(0, -1);
    }

    @Override
    public void setGatingSequences(final Sequence... sequences)
    {
        this.gatingSequences = sequences;
    }

    @Override
    public SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
    {
        return new ProcessingSequenceBarrier(waitStrategy, cursor, sequencesToTrack);
    }

    @Override
    public int getBufferSize()
    {
        return bufferSize;
    }

    @Override
    public long getCursor()
    {
        return cursor.get();
    }

    @Override
    public boolean hasAvailableCapacity(final int requiredCapacity)
    {
        return hasAvailableCapacity(cursor.get(), requiredCapacity, gatingSequences);
    }
    
    @Override
    public long next()
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }
        
        long current;
        long next;
        
        do
        {
            current = cursor.get();
            next = current + 1;
            
            if (next > wrapPointCache.get())
            {
                long wrapPoint = getMinimumSequence(gatingSequences) + bufferSize;
                wrapPointCache.set(wrapPoint);
        
                if (next > wrapPoint)
                {
                    LockSupport.parkNanos(1);
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
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }
        
        long current;
        long next;
        
        do
        {
            current = cursor.get();
            next = current + 1;

            if (next > wrapPointCache.get())
            {
                long wrapPoint = getMinimumSequence(gatingSequences) + bufferSize;
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
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        cursor.set(sequence);
        waitForFreeSlotAt(sequence, gatingSequences);

        return sequence;
    }

    @Override
    public void publish(final long sequence)
    {
        setAvailable(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void forcePublish(final long sequence)
    {
        cursor.set(sequence);
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
    public long remainingCapacity()
    {
        long consumed = Util.getMinimumSequence(gatingSequences);
        long produced = cursor.get();
        return getBufferSize() - (produced - consumed);
    }

    @Override
    public void ensureAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        long bufferAddress = (index * scale) + base;
        while (UNSAFE.getIntVolatile(availableBuffer, bufferAddress) != flag)
        {
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
        
    private boolean hasAvailableCapacity(long sequence, final int requiredCapacity, final Sequence[] dependentSequences)
    {
        final long desiredSequence = sequence + requiredCapacity;
        if (desiredSequence > wrapPointCache.get())
        {
            long wrapPoint = getMinimumSequence(dependentSequences) + bufferSize;
            wrapPointCache.set(wrapPoint);
    
            if (desiredSequence > wrapPoint)
            {
                return false;
            }
        }
    
        return true;
    }

    private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences)
    {
        final long wrapPoint = sequence - bufferSize;
        if (wrapPoint > wrapPointCache.get())
        {
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
            {
                LockSupport.parkNanos(1L);
            }
    
            wrapPointCache.set(minSequence);
        }
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
