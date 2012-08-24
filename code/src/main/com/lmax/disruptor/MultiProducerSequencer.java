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

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.util.MutableLong;
import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public class MultiProducerSequencer implements Sequencer
{
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
    private Sequence[] gatingSequences;
    private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
    {
        @Override
        protected MutableLong initialValue()
        {
            return new MutableLong(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
        }
    };

    private final AtomicIntegerArray availableBuffer;
    private final int bufferSize;
    private final int indexMask;
    private final int indexShift;

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
    public BatchDescriptor newBatchDescriptor(final int size)
    {
        return new BatchDescriptor(Math.min(size, bufferSize));
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
        
        return incrementAndGet(1, gatingSequences);    
    }
    
    @Override
    public long tryNext(int requiredCapacity) throws InsufficientCapacityException
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }
        
        if (requiredCapacity < 1)
        {
            throw new IllegalArgumentException("Available capacity must be greater than 0");
        }
        
        long current;
        long next;
        
        do
        {
            current = cursor.get();
            next = cursor.get() + 1;
            
            if (!hasAvailableCapacity(current, 1, gatingSequences))
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
        while (!cursor.compareAndSet(current, next));

        return next;    
    }

    @Override
    public BatchDescriptor next(final BatchDescriptor batchDescriptor)
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        final long sequence = incrementAndGet(batchDescriptor.getSize(), gatingSequences);
        batchDescriptor.setEnd(sequence);
        return batchDescriptor;
    }

    @Override
    public long claim(final long sequence)
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        cursor.set(sequence);
        waitForFreeSlotAt(sequence, gatingSequences, minGatingSequenceThreadLocal.get());

        return sequence;
    }

    @Override
    public void publish(final long sequence)
    {
        publish(sequence, 1);
    }

    @Override
    public void publish(final BatchDescriptor batchDescriptor)
    {
        publish(batchDescriptor.getEnd(), batchDescriptor.getSize());
    }

    private void publish(final long sequence, final int batchSize)
    {
        long batchSequence = sequence - batchSize;
        do
        {            
            setAvailable(++batchSequence);
        }
        while (sequence != batchSequence);
        
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
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        availableBuffer.lazySet(index, flag);
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
        while (availableBuffer.get(index) != flag)
        {
            // spin
        }
    }

    @Override
    public boolean isAvailable(long sequence)
    {
        int index = calculateIndex(sequence);
        int flag = calculateAvailabilityFlag(sequence);
        return availableBuffer.get(index) == flag;
    }
    
    private boolean hasAvailableCapacity(long sequence, final int requiredCapacity, final Sequence[] dependentSequences)
    {
        final long wrapPoint = (sequence + requiredCapacity) - bufferSize;
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

    private long incrementAndGet(final int delta, final Sequence[] dependentSequences)
    {
        long current;
        long next;
        
        do
        {
            current = cursor.get();
            next = cursor.get() + delta;
            
            if (!hasAvailableCapacity(current, delta, gatingSequences))
            {
                LockSupport.parkNanos(1);
                continue;
            }
            else if (cursor.compareAndSet(current, next))
            {
                break;
            }
        }
        while (true);
    
        return next;
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

    private int calculateAvailabilityFlag(final long sequence)
    {
        return (int) (sequence >>> indexShift) & 1;
    }

    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
