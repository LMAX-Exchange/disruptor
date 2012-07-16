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
public class SingleProducerSequencer implements Sequencer
{
    /** Set to -1 as sequence starting point */
    private final PaddedLong minGatingSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
    private final PaddedLong claimSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);

    private final Sequence cursor = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
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
        return hasAvailableCapacity(requiredCapacity, gatingSequences);
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
        
        return checkAndIncrement(requiredCapacity, 1, gatingSequences);
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

        claimSequence.set(sequence);
        waitForFreeSlotAt(sequence, gatingSequences);

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

    @Override
    public void forcePublish(final long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    private void publish(final long sequence, final int batchSize)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
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
    
    private long checkAndIncrement(int requiredCapacity, int delta, Sequence[] dependentSequences) 
            throws InsufficientCapacityException
    {
        if (!hasAvailableCapacity(requiredCapacity, dependentSequences))
        {
            throw InsufficientCapacityException.INSTANCE;
        }
        
        return incrementAndGet(delta, dependentSequences);
    }
    
    private boolean hasAvailableCapacity(final int requiredCapacity, final Sequence[] dependentSequences)
    {
        final long wrapPoint = (claimSequence.get() + requiredCapacity) - bufferSize;
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
        long nextSequence = claimSequence.get() + delta;
        claimSequence.set(nextSequence);
        waitForFreeSlotAt(nextSequence, dependentSequences);

        return nextSequence;
    }
}
