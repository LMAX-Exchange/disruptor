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
    private static final Unsafe UNSAFE = Util.getUnsafe();
    private static final long BYTE_ARRAY_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);
    private static final int SIZE_OFFSET = 8;
    private static final int PREV_OFFSET = SIZE_OFFSET + 4;
    private static final int BODY_OFFSET = PREV_OFFSET + 8;
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private Sequence[] gatingSequences;
    private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
    {
        @Override
        protected MutableLong initialValue()
        {
            return new MutableLong(Sequencer.INITIAL_CURSOR_VALUE);
        }
    };

    private final long address;
    private final int bufferSize;
    private final int indexMask;
    private final int bodySize;
    private final int chunkSize;
    private final Object buffer;
    private final Sequence wrapPointCache = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

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
        long current;
        long next;
        
        do
        {
            current = cursor.get();
            next = current + 1;
            
            if (next > wrapPointCache .get())
            {
                long wrapPoint = getMinimumSequence(gatingSequences, current) + bufferSize;
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
                long wrapPoint = getMinimumSequence(gatingSequences, current) + bufferSize;
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
        waitForFreeSlotAt(sequence, gatingSequences, minGatingSequenceThreadLocal.get());

        return sequence;
    }

    @Override
    public void publish(final long sequence)
    {
        publish(sequence, 1);
    }

    public void put(byte[] data, int dataOffset, int dataLength)
    {
        checkArray(data, dataOffset, dataLength);
        
        int end = dataOffset + dataLength;
        int current = dataOffset;
        int remaining = dataLength;
        long lastSequence = -1;
        do 
        {
            int toCopy = Math.min(remaining, bodySize);
            
            long next = next();
            long chunkAddress = calculateAddress(next);
            setBodySize(chunkAddress, dataLength);
            setPreviousSequence(chunkAddress, lastSequence);
            UNSAFE.copyMemory(data, BYTE_ARRAY_OFFSET + current, null, chunkAddress + BODY_OFFSET, toCopy);
            publish(next);
            
            lastSequence = next;
            current += toCopy;
        } 
        while (current < end);
    }

    public int getEntrySize(long sequence)
    {
        long dataAddress = calculateAddress(sequence);
        return getBodySize(dataAddress);
    }

    public void getBody(long sequence, byte[] data, int offset, int length)
    {
        checkArray(data, offset, length);
        long chunkAddress = calculateAddress(sequence);
        int bodySize = getBodySize(chunkAddress);
        int toCopy = Math.min(bodySize, length);
        UNSAFE.copyMemory(null, chunkAddress + BODY_OFFSET, data, BYTE_ARRAY_OFFSET + offset, toCopy);
    }

    public void getHeader(long sequence, byte[] header, int offset, int length)
    {
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
        int offset = calculateOffset(sequence);
        UNSAFE.putOrderedLong(null, address + offset, sequence);
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
        while (UNSAFE.getLongVolatile(null, sequenceAddress) != sequence)
        {
            // spin.
        }
    }
    
    @Override
    public boolean isAvailable(long sequence)
    {
        int offset = calculateOffset(sequence);
        long sequenceAddress = address + offset;
        return UNSAFE.getLongVolatile(null, sequenceAddress) == sequence;
    }
    
    @Override
    public String toString()
    {
        return "MultiProducerOffHeapRingBuffer [address=" + address + ", bufferSize=" + bufferSize + ", chunkSize=" + chunkSize + ", buffer=" +
               buffer + "]";
    }
    
    public final int getBodySize()
    {
        return bodySize;
    }

    public long getPreviousSequence(long sequence)
    {
        long chunkAddress = calculateAddress(sequence);
        return getPreviousSequence0(chunkAddress);
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

    private void setBodySize(long chunkAddress, int dataLength)
    {
        UNSAFE.putInt(chunkAddress + SIZE_OFFSET, dataLength);
    }
    
    private int getBodySize(long chunkAddress)
    {
        return UNSAFE.getInt(chunkAddress + SIZE_OFFSET);
    }

    private void setPreviousSequence(long chunkAddress, long lastSequence)
    {
        UNSAFE.putLong(chunkAddress + PREV_OFFSET, lastSequence);
    }

    private long getPreviousSequence0(long chunkAddress)
    {
        return UNSAFE.getLong(chunkAddress + PREV_OFFSET);
    }

    private void checkArray(byte[] data, int offset, int length)
    {
        if (offset + length > data.length)
        {
            throw new IllegalArgumentException("Index out of bounds: data.length = " + data.length + ", offset = " + offset + ", lenth = " + length);
        }
    }
}
