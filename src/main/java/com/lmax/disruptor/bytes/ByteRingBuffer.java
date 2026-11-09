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
package com.lmax.disruptor.bytes;

import com.lmax.disruptor.FixedSequenceGroup;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.util.Util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class ByteRingBufferLhsPad
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;
}

abstract class ByteRingBufferSharedFields extends ByteRingBufferLhsPad
{
    static final AtomicReferenceFieldUpdater<ByteRingBufferSharedFields, Sequence[]> SEQUENCE_UPDATER =
        AtomicReferenceFieldUpdater.newUpdater(ByteRingBufferSharedFields.class, Sequence[].class, "gatingSequences");

    final byte[] buffer;
    final int capacity;
    final int mask;
    final int maxPayloadLength;
    final WaitStrategy waitStrategy;
    final Sequence cursor = new Sequence(0L);
    volatile Sequence[] gatingSequences = new Sequence[0];

    ByteRingBufferSharedFields(final int capacity, final WaitStrategy waitStrategy)
    {
        this.buffer = new byte[capacity];
        this.capacity = capacity;
        this.mask = capacity - 1;
        this.maxPayloadLength = (capacity / ByteRingBuffer.MAX_RECORD_FRACTION) - ByteRingBuffer.HEADER_LENGTH;
        this.waitStrategy = waitStrategy;
    }
}

abstract class ByteRingBufferMidPad extends ByteRingBufferSharedFields
{
    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    ByteRingBufferMidPad(final int capacity, final WaitStrategy waitStrategy)
    {
        super(capacity, waitStrategy);
    }
}

abstract class ByteRingBufferProducerFields extends ByteRingBufferMidPad
{
    long nextPosition = 0L;
    long cachedGatingPosition = 0L;
    long abortPosition = -1L;

    ByteRingBufferProducerFields(final int capacity, final WaitStrategy waitStrategy)
    {
        super(capacity, waitStrategy);
    }
}

/**
 * Ring based store of variable length records, written contiguously into one {@code byte[]} by a single producer.
 * Sequences are byte positions.
 *
 * <p>Records are claimed, written, then published. A claim that refreshes the cached gating sequence publishes
 * the records claimed before it, so write each record before claiming the next.
 */
public final class ByteRingBuffer extends ByteRingBufferProducerFields
{
    /** Length in bytes of the record header */
    public static final int HEADER_LENGTH = 8;
    /** Alignment in bytes of every record */
    public static final int ALIGNMENT = 8;
    /** Type id of the padding record inserted when a record doesn't fit before the end of the buffer */
    public static final int PADDING_TYPE_ID = -1;
    /** Returned by {@link #tryClaim(int, int)} when there is not enough capacity */
    public static final int INSUFFICIENT_CAPACITY = -2;
    /** Minimum capacity in bytes */
    public static final int MIN_CAPACITY = 128;

    static final int MAX_RECORD_FRACTION = 8;
    static final int TYPE_OFFSET = 4;

    private static final VarHandle LONG = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    protected byte
        p10, p11, p12, p13, p14, p15, p16, p17,
        p20, p21, p22, p23, p24, p25, p26, p27,
        p30, p31, p32, p33, p34, p35, p36, p37,
        p40, p41, p42, p43, p44, p45, p46, p47,
        p50, p51, p52, p53, p54, p55, p56, p57,
        p60, p61, p62, p63, p64, p65, p66, p67,
        p70, p71, p72, p73, p74, p75, p76, p77;

    /**
     * Construct a ByteRingBuffer with the selected wait strategy and capacity.
     *
     * @param capacity     in bytes, a power of 2 and at least {@link #MIN_CAPACITY}.
     * @param waitStrategy for those waiting on the cursor.
     * @throws IllegalArgumentException if capacity is less than {@link #MIN_CAPACITY} or not a power of 2
     */
    public ByteRingBuffer(final int capacity, final WaitStrategy waitStrategy)
    {
        super(checkCapacity(capacity), waitStrategy);
    }

    private static int checkCapacity(final int capacity)
    {
        if (capacity < MIN_CAPACITY || Integer.bitCount(capacity) != 1)
        {
            throw new IllegalArgumentException("capacity must be a power of 2 and >= " + MIN_CAPACITY + ": " + capacity);
        }
        return capacity;
    }

    /**
     * Round a length up to the record alignment.
     *
     * @param length in bytes.
     * @return the length rounded up to a multiple of {@link #ALIGNMENT}.
     */
    public static int align(final int length)
    {
        return (length + ALIGNMENT - 1) & -ALIGNMENT;
    }

    /**
     * Get the array the records are stored in.
     *
     * @return the backing array.
     */
    public byte[] buffer()
    {
        return buffer;
    }

    /**
     * Get the capacity of the buffer.
     *
     * @return the capacity in bytes.
     */
    public int capacity()
    {
        return capacity;
    }

    /**
     * Get the current cursor value for the ring buffer.
     *
     * @return the position up to which records have been published.
     */
    public long getCursor()
    {
        return cursor.get();
    }

    /**
     * Add the specified gating sequences to this instance of the ByteRingBuffer.  They will safely and atomically
     * be added to the list of gating sequences, starting at the cursor.
     *
     * @param sequences The sequences to add.
     */
    public void addGatingSequences(final Sequence... sequences)
    {
        long cursorPosition;
        Sequence[] current;
        Sequence[] updated;
        do
        {
            current = gatingSequences;
            updated = Arrays.copyOf(current, current.length + sequences.length);
            cursorPosition = cursor.get();
            for (int i = 0; i < sequences.length; i++)
            {
                sequences[i].set(cursorPosition);
                updated[current.length + i] = sequences[i];
            }
        }
        while (!SEQUENCE_UPDATER.compareAndSet(this, current, updated));

        cursorPosition = cursor.get();
        for (final Sequence sequence : sequences)
        {
            sequence.set(cursorPosition);
        }
    }

    /**
     * Create a new SequenceBarrier to be used by a ByteRecordProcessor to track which records
     * are available to be read from the ring buffer given a list of sequences to track.
     *
     * @param dependentSequences the additional sequences to track
     * @return A sequence barrier that will track the specified sequences.
     */
    public SequenceBarrier newBarrier(final Sequence... dependentSequences)
    {
        return new ByteSequenceBarrier(waitStrategy, cursor, dependentSequence(dependentSequences));
    }

    private Sequence dependentSequence(final Sequence[] dependentSequences)
    {
        if (dependentSequences.length == 0)
        {
            return cursor;
        }
        if (dependentSequences.length == 1)
        {
            return dependentSequences[0];
        }
        return new FixedSequenceGroup(dependentSequences);
    }

    /**
     * Claim space for a record and write its header, if the space is available.
     *
     * @param typeId application defined record type, must be &gt;= 0.
     * @param length number of payload bytes in the record.
     * @return offset in {@link #buffer()} at which to write the payload, or {@link #INSUFFICIENT_CAPACITY}.
     */
    public int tryClaim(final int typeId, final int length)
    {
        if (typeId < 0)
        {
            throw new IllegalArgumentException("typeId must be >= 0: " + typeId);
        }
        if (length < 0 || length > maxPayloadLength)
        {
            throw new IllegalArgumentException("length must be in [0, " + maxPayloadLength + "]: " + length);
        }

        final int recordLength = HEADER_LENGTH + length;
        final int alignedLength = align(recordLength);
        final long position = nextPosition;
        final int index = (int) position & mask;
        final int toEnd = capacity - index;
        final int padding = alignedLength > toEnd ? toEnd : 0;

        final long wrapPoint = position + padding + alignedLength - capacity;
        if (wrapPoint > cachedGatingPosition && !hasAvailableCapacity(wrapPoint))
        {
            return INSUFFICIENT_CAPACITY;
        }

        int recordIndex = index;
        if (padding != 0)
        {
            putHeader(index, padding, PADDING_TYPE_ID);
            recordIndex = 0;
        }
        putHeader(recordIndex, recordLength, typeId);
        abortPosition = position;
        nextPosition = position + padding + alignedLength;

        return recordIndex + HEADER_LENGTH;
    }

    private boolean hasAvailableCapacity(final long wrapPoint)
    {
        cursor.setVolatile(nextPosition);  // StoreLoad fence
        waitStrategy.signalAllWhenBlocking();
        final long minimum = Util.getMinimumSequence(gatingSequences, nextPosition);
        cachedGatingPosition = minimum;
        return wrapPoint <= minimum;
    }

    /**
     * Discard the latest claim, including any padding record it inserted.
     *
     * @throws IllegalStateException if the latest claim has been published or aborted
     */
    public void abort()
    {
        final long position = abortPosition;
        if (position < 0 || position < cursor.get())
        {
            throw new IllegalStateException("no unpublished claim to abort");
        }
        nextPosition = position;
        abortPosition = -1L;
    }

    /**
     * Publish the claimed records, making them available to consumers.
     */
    public void publish()
    {
        cursor.set(nextPosition);
        waitStrategy.signalAllWhenBlocking();
    }

    /**
     * Non-blocking write of a record: claim, copy and publish it if there is space.
     *
     * @param typeId application defined record type, must be &gt;= 0.
     * @param src    source array.
     * @param offset offset in the source array.
     * @param length number of bytes to copy.
     * @return true if written, false if there was insufficient capacity.
     */
    public boolean write(final int typeId, final byte[] src, final int offset, final int length)
    {
        Objects.checkFromIndexSize(offset, length, src.length);
        final int index = tryClaim(typeId, length);
        if (index == INSUFFICIENT_CAPACITY)
        {
            return false;
        }
        System.arraycopy(src, offset, buffer, index, length);
        publish();
        return true;
    }

    /**
     * Get the remaining capacity for this ring buffer, ignoring any padding needed at the end of the buffer.
     *
     * @return The number of bytes remaining.
     */
    public long remainingCapacity()
    {
        final long produced = nextPosition;
        return capacity - (produced - Util.getMinimumSequence(gatingSequences, produced));
    }

    static long headerAt(final byte[] buffer, final int index)
    {
        return (long) LONG.get(buffer, index);
    }

    private void putHeader(final int index, final int recordLength, final int typeId)
    {
        // Little endian: the length is the low half and the type id the high half
        LONG.set(buffer, index, ((long) typeId << (TYPE_OFFSET * Byte.SIZE)) | Integer.toUnsignedLong(recordLength));
    }

    @Override
    public String toString()
    {
        return "ByteRingBuffer{" +
            "capacity=" + capacity +
            ", cursor=" + cursor.get() +
            ", nextPosition=" + nextPosition +
            ", gatingSequences=" + Arrays.toString(gatingSequences) +
            '}';
    }
}
