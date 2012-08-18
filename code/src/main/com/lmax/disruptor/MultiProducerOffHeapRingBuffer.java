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

import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;

import sun.misc.Unsafe;

import com.lmax.disruptor.util.MutableLong;
import com.lmax.disruptor.util.Util;


/**
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public class MultiProducerOffHeapRingBuffer implements Sequencer
{
    private static final Unsafe unsafe = Util.getUnsafe();
    private static final long BYTE_ARRAY_OFFSET = unsafe.arrayBaseOffset(byte[].class);
    private static final int SIZE_OFFSET = 8;
    private static final int PREV_OFFSET = SIZE_OFFSET + 4;
    private static final int BODY_OFFSET = PREV_OFFSET + 8;
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(MultiProducerOffHeapRingBuffer.INITIAL_CURSOR_VALUE);
    private final Sequence claimSequence = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
    private Sequence[] gatingSequences;
    private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
    {
        @Override
        protected MutableLong initialValue()
        {
            return new MutableLong(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
        }
    };

    private final long address;
    private final int bufferSize;
    private final int indexMask;
    private final int bodySize;
    private final int chunkSize;
    private final Object buffer;

    private MultiProducerOffHeapRingBuffer(Object buffer, long address, int bufferSize, int chunkSize, WaitStrategy waitStrategy)
    {
        this.buffer = buffer;
        this.address = address;
        this.bufferSize = bufferSize;
        this.chunkSize = chunkSize;
        this.bodySize = chunkSize - BODY_OFFSET;
        this.waitStrategy = waitStrategy;
        this.indexMask = bufferSize - 1;
    }
    
    public static MultiProducerOffHeapRingBuffer newInstance(int bufferSize, int chunkSize, WaitStrategy waitStrategy)
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize * chunkSize);
        long address = Util.getAddressFromDirectByteBuffer(buffer);
        
        return new MultiProducerOffHeapRingBuffer(buffer, address, bufferSize, chunkSize, waitStrategy);
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
        return hasAvailableCapacity(claimSequence.get(), requiredCapacity, gatingSequences);
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
    
    public void put(byte[] data, int offset, int length)
    {
        if (offset + length > data.length)
        {
            throw new IllegalArgumentException("Index out of bounds: data.length = " + data.length + ", offset = " + offset + ", lenth = " + length);
        }
        
        int current = offset;
        int remaining = length;
        int end = offset + length;
        long lastSequence = -1;
        do 
        {
            int toCopy = Math.min(remaining, bodySize);
            
            long next = next();
            long chunkAddress = calculateAddress(next);
            unsafe.putInt(chunkAddress + SIZE_OFFSET, toCopy);
            unsafe.putLong(chunkAddress + PREV_OFFSET, lastSequence);
            unsafe.copyMemory(data, BYTE_ARRAY_OFFSET + current, null, chunkAddress + BODY_OFFSET, toCopy);
            publish(next);
            
            lastSequence = next;
            current += toCopy;
        } 
        while (current < end);
    }

    public int getEntrySize(long sequence)
    {
        long dataAddress = calculateAddress(sequence);
        return unsafe.getInt(dataAddress + SIZE_OFFSET);
    }


    public void getData(int i, byte[] read, int j, int length)
    {
        // TODO Auto-generated method stub
        
    }

    private void publish(final long sequence, final int batchSize)
    {
        long batchSequence = sequence - batchSize;
        do
        {            
            setAvailable(++batchSequence);
        }
        while (sequence != batchSequence);
        
        long cursorSequnece;
        while ((cursorSequnece = cursor.get()) < sequence)
        {
            if (cursor.compareAndSet(cursorSequnece, sequence))
            {
                break;
            }
        }
        
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
        int offset = calculateOffset(sequence);
        unsafe.putOrderedLong(null, address + offset, sequence);
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
        int offset = calculateOffset(sequence);
        long sequenceAddress = address + offset;
        while (unsafe.getLongVolatile(null, sequenceAddress) != sequence)
        {
            // spin.
        }
    }
    
    @Override
    public String toString()
    {
        return "MultiProducerOffHeapRingBuffer [address=" + address + ", bufferSize=" + bufferSize + ", chunkSize=" + chunkSize + ", buffer=" +
               buffer + "]";
    }

    private long checkAndIncrement(int requiredCapacity, int delta, Sequence[] dependentSequences) 
            throws InsufficientCapacityException
    {
        for (;;)
        {
            long sequence = claimSequence.get();
            if (hasAvailableCapacity(sequence, requiredCapacity, gatingSequences))
            {
                long nextSequence = sequence + delta;
                if (claimSequence.compareAndSet(sequence, nextSequence))
                {
                    return nextSequence;
                }
            }
            else
            {
                throw InsufficientCapacityException.INSTANCE;
            }
        }
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
        final long nextSequence = claimSequence.addAndGet(delta);
        waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequenceThreadLocal.get());
    
        return nextSequence;
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

    private long calculateAddress(final long sequence)
    {
        return (calculateIndex(sequence) * chunkSize) + address;
    }

    private int calculateOffset(final long sequence)
    {
        return calculateIndex(sequence) * chunkSize;
    }
    
    private int calculateIndex(final long sequence)
    {
        return ((int) sequence) & indexMask;
    }
}
