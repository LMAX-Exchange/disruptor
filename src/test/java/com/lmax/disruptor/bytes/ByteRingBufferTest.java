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

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.support.DummyWaitStrategy;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.bytes.ByteRingBufferRecordMatcher.ringBufferWithRecords;
import static com.lmax.disruptor.bytes.ByteRingBufferRecordMatcher.ringBufferWithRecordsFrom;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ByteRingBufferTest
{
    private static final int CAPACITY = 256;
    private static final String MAX_PAYLOAD = "0123456789abcdefghijklmn";
    private static final int MAX_RECORD_LENGTH = 32;
    private static final int RECORDS_PER_LAP = CAPACITY / MAX_RECORD_LENGTH;

    private final ExecutorService executor = Executors.newFixedThreadPool(2, DaemonThreadFactory.INSTANCE);
    private final ByteRingBuffer ringBuffer = new ByteRingBuffer(CAPACITY, new BusySpinWaitStrategy());
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    private final Sequence gatingSequence = new Sequence();

    public ByteRingBufferTest()
    {
        ringBuffer.addGatingSequences(gatingSequence);
    }

    @Test
    public void shouldClaimAndGet() throws Exception
    {
        assertEquals(0L, ringBuffer.getCursor());

        claimAndCopy(7, "hello");
        ringBuffer.publish();

        assertEquals(16L, sequenceBarrier.waitFor(1L));
        assertThat(ringBuffer, ringBufferWithRecords("7:hello"));
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        final CyclicBarrier started = new CyclicBarrier(2);
        final Future<String> record = executor.submit(() ->
        {
            started.await(2, TimeUnit.SECONDS);
            sequenceBarrier.waitFor(1L);
            final long header = ByteRingBuffer.headerAt(ringBuffer.buffer(), 0);
            final int length = (int) header - ByteRingBuffer.HEADER_LENGTH;
            return (int) (header >>> 32) + ":" + new String(ringBuffer.buffer(), ByteRingBuffer.HEADER_LENGTH, length, StandardCharsets.US_ASCII);
        });
        started.await(2, TimeUnit.SECONDS);

        write(7, "hello");

        assertEquals("7:hello", record.get(2, TimeUnit.SECONDS));
    }

    @Test
    public void shouldClaimAndGetMultipleRecords()
    {
        write(7, "hello");
        write(8, "world!");

        assertThat(ringBuffer, ringBufferWithRecords("7:hello", "8:world!"));
    }

    @Test
    public void shouldAlignRecordsToEightBytes()
    {
        write(1, "abcd");
        assertEquals(16L, ringBuffer.getCursor());

        write(1, "abcdefgh");
        assertEquals(32L, ringBuffer.getCursor());

        write(1, "abcdefghi");
        assertEquals(56L, ringBuffer.getCursor());
    }

    @Test
    public void shouldWriteHeaderOnClaim()
    {
        final int index = ringBuffer.tryClaim(5, 10) - ByteRingBuffer.HEADER_LENGTH;

        final long header = ByteRingBuffer.headerAt(ringBuffer.buffer(), index);

        assertEquals(ByteRingBuffer.HEADER_LENGTH + 10, (int) header);
        assertEquals(5, (int) (header >>> 32));
    }

    @Test
    public void shouldNotBeAvailableUntilPublished()
    {
        claimAndCopy(1, "abc");
        claimAndCopy(2, "def");

        assertEquals(0L, ringBuffer.getCursor());

        ringBuffer.publish();

        assertThat(ringBuffer, ringBufferWithRecords("1:abc", "2:def"));
    }

    @Test
    public void shouldNotifyWaitStrategyOnPublish()
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final ByteRingBuffer ringBuffer = new ByteRingBuffer(CAPACITY, waitStrategy);

        ringBuffer.tryClaim(1, 8);
        ringBuffer.publish();

        assertEquals(1, waitStrategy.signalAllWhenBlockingCalls);
    }

    @Test
    public void shouldClaimMaximumPayload()
    {
        write(1, MAX_PAYLOAD);

        assertEquals(MAX_RECORD_LENGTH, ringBuffer.getCursor());
        assertThat(ringBuffer, ringBufferWithRecords("1:" + MAX_PAYLOAD));
    }

    @Test
    public void shouldNotAllowNegativeTypeId()
    {
        assertThrows(IllegalArgumentException.class, () -> ringBuffer.tryClaim(-1, 8));
    }

    @Test
    public void shouldNotAllowNegativeLength()
    {
        assertThrows(IllegalArgumentException.class, () -> ringBuffer.tryClaim(1, -1));
    }

    @Test
    public void shouldNotAllowLengthGreaterThanMaximumPayload()
    {
        assertThrows(IllegalArgumentException.class, () -> ringBuffer.tryClaim(1, MAX_PAYLOAD.length() + 1));
    }

    @Test
    public void shouldNotWriteWhenSourceRangeExtendsPastEndOfArray()
    {
        assertThrows(IndexOutOfBoundsException.class, () -> ringBuffer.write(1, new byte[8], 4, 8));

        assertEquals(CAPACITY, ringBuffer.remainingCapacity());
    }

    @Test
    public void shouldNotAllowCapacityThatIsNotAPowerOfTwo()
    {
        assertThrows(IllegalArgumentException.class, () -> new ByteRingBuffer(CAPACITY + ByteRingBuffer.ALIGNMENT, new BusySpinWaitStrategy()));
    }

    @Test
    public void shouldNotAllowCapacityBelowMinimum()
    {
        assertThrows(IllegalArgumentException.class, () -> new ByteRingBuffer(ByteRingBuffer.MIN_CAPACITY / 2, new BusySpinWaitStrategy()));
    }

    @Test
    public void shouldWrap()
    {
        for (int i = 0; i < RECORDS_PER_LAP + 3; i++)
        {
            write(i, MAX_PAYLOAD);
            gatingSequence.set(ringBuffer.getCursor());
        }

        assertEquals(CAPACITY + 3L * MAX_RECORD_LENGTH, ringBuffer.getCursor());
        assertThat(ringBuffer, ringBufferWithRecordsFrom(CAPACITY, "8:" + MAX_PAYLOAD, "9:" + MAX_PAYLOAD, "10:" + MAX_PAYLOAD));
    }

    @Test
    public void shouldInsertPaddingWhenRecordDoesNotFitBeforeEnd()
    {
        writeMaxPayloads(RECORDS_PER_LAP - 1);
        write(1, "abcdefgh");
        gatingSequence.set(ringBuffer.getCursor());

        write(9, MAX_PAYLOAD);

        assertEquals(CAPACITY + MAX_RECORD_LENGTH, ringBuffer.getCursor());
        assertThat(ringBuffer, ringBufferWithRecordsFrom(224L, "1:abcdefgh", "9:" + MAX_PAYLOAD));
    }

    @Test
    public void shouldPreventWrapping()
    {
        writeMaxPayloads(RECORDS_PER_LAP);

        assertFalse(tryWrite(1, MAX_PAYLOAD));
    }

    @Test
    public void shouldReturnInsufficientCapacityWhenBufferIsFull()
    {
        writeMaxPayloads(RECORDS_PER_LAP);

        assertEquals(ByteRingBuffer.INSUFFICIENT_CAPACITY, ringBuffer.tryClaim(1, MAX_PAYLOAD.length()));
    }

    @Test
    public void shouldClaimWhenConsumerFreesSpace()
    {
        writeMaxPayloads(RECORDS_PER_LAP);

        gatingSequence.set(MAX_RECORD_LENGTH);

        assertTrue(tryWrite(9, MAX_PAYLOAD));
    }

    @Test
    public void shouldCalculateRemainingCapacity()
    {
        assertEquals(CAPACITY, ringBuffer.remainingCapacity());

        for (int i = 1; i <= RECORDS_PER_LAP; i++)
        {
            write(1, MAX_PAYLOAD);
            assertEquals(CAPACITY - (long) i * MAX_RECORD_LENGTH, ringBuffer.remainingCapacity());
        }
    }

    @Test
    public void shouldDiscardOnlyLatestClaimOnAbort()
    {
        claimAndCopy(1, "abc");
        claimAndCopy(2, "def");
        ringBuffer.abort();
        claimAndCopy(3, "ghi");
        ringBuffer.publish();

        assertThat(ringBuffer, ringBufferWithRecords("1:abc", "3:ghi"));
    }

    @Test
    public void shouldDiscardPaddingInsertedByAbortedClaim()
    {
        writeMaxPayloads(RECORDS_PER_LAP - 1);
        gatingSequence.set(ringBuffer.getCursor());
        claimAndCopy(1, "abcdefgh");
        ringBuffer.tryClaim(2, MAX_PAYLOAD.length());
        ringBuffer.abort();

        write(3, "ijklmnop");

        assertEquals(CAPACITY, ringBuffer.getCursor());
        assertThat(ringBuffer, ringBufferWithRecordsFrom(224L, "1:abcdefgh", "3:ijklmnop"));
    }

    @Test
    public void shouldNotAllowAbortWithoutClaim()
    {
        assertThrows(IllegalStateException.class, ringBuffer::abort);
    }

    @Test
    public void shouldNotAllowSecondAbort()
    {
        ringBuffer.tryClaim(1, 3);
        ringBuffer.abort();

        assertThrows(IllegalStateException.class, ringBuffer::abort);
    }

    @Test
    public void shouldNotAllowAbortAfterPublish()
    {
        ringBuffer.tryClaim(1, 3);
        ringBuffer.publish();

        assertThrows(IllegalStateException.class, ringBuffer::abort);
    }

    @Test
    public void shouldPublishClaimedRecordsWhenRefreshingGatingSequences()
    {
        final ByteRingBuffer ungatedRingBuffer = new ByteRingBuffer(128, new BusySpinWaitStrategy());
        for (int i = 0; i < 8; i++)
        {
            ungatedRingBuffer.tryClaim(1, 8);
        }
        assertEquals(0L, ungatedRingBuffer.getCursor());

        ungatedRingBuffer.tryClaim(1, 8);

        assertEquals(128L, ungatedRingBuffer.getCursor());
    }

    @Test
    public void shouldSetAddedGatingSequenceToPublishedCursor()
    {
        write(1, "abcdefgh");
        ringBuffer.tryClaim(2, 8);
        final Sequence sequence = new Sequence();

        ringBuffer.addGatingSequences(sequence);

        assertEquals(16L, sequence.get());
    }

    @Test
    public void shouldGateOnSequenceAddedAfterProducerHasWrapped()
    {
        final ByteRingBuffer ungatedRingBuffer = new ByteRingBuffer(128, new BusySpinWaitStrategy());
        tryWriteRecords(ungatedRingBuffer, 9);

        ungatedRingBuffer.addGatingSequences(new Sequence());

        tryWriteRecords(ungatedRingBuffer, 8);
        assertFalse(ungatedRingBuffer.write(1, new byte[8], 0, 8));
    }

    @Test
    public void shouldMoveAddedGatingSequenceToCursorWhenProducerWrapsDuringRegistration() throws Exception
    {
        final ByteRingBuffer ungatedRingBuffer = new ByteRingBuffer(128, new BusySpinWaitStrategy());
        final PausingSequence sequence = new PausingSequence();

        final Future<?> registration = executor.submit(() -> ungatedRingBuffer.addGatingSequences(sequence));
        assertTrue(sequence.awaitFirstSet(2, TimeUnit.SECONDS));
        tryWriteRecords(ungatedRingBuffer, 9);
        sequence.resume();
        registration.get(2, TimeUnit.SECONDS);

        assertEquals(144L, sequence.get());
    }

    @Test
    public void shouldKeepBothGatingSequencesWhenRegistrationsRace() throws Exception
    {
        final ByteRingBuffer ungatedRingBuffer = new ByteRingBuffer(128, new BusySpinWaitStrategy());
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final Sequence first = new RendezvousSequence(barrier);
        final Sequence second = new RendezvousSequence(barrier);

        final Future<?> addFirst = executor.submit(() -> ungatedRingBuffer.addGatingSequences(first));
        final Future<?> addSecond = executor.submit(() -> ungatedRingBuffer.addGatingSequences(second));
        addFirst.get(2, TimeUnit.SECONDS);
        addSecond.get(2, TimeUnit.SECONDS);
        tryWriteRecords(ungatedRingBuffer, 8);

        first.set(128L);
        assertFalse(ungatedRingBuffer.write(1, new byte[8], 0, 8));

        first.set(0L);
        second.set(128L);
        assertFalse(ungatedRingBuffer.write(1, new byte[8], 0, 8));
    }

    private int claimAndCopy(final int typeId, final String payload)
    {
        final byte[] bytes = bytes(payload);
        final int offset = ringBuffer.tryClaim(typeId, bytes.length);
        System.arraycopy(bytes, 0, ringBuffer.buffer(), offset, bytes.length);
        return offset;
    }

    private void write(final int typeId, final String payload)
    {
        final byte[] bytes = bytes(payload);
        assertTrue(ringBuffer.write(typeId, bytes, 0, bytes.length));
    }

    private boolean tryWrite(final int typeId, final String payload)
    {
        final byte[] bytes = bytes(payload);
        return ringBuffer.write(typeId, bytes, 0, bytes.length);
    }

    private void writeMaxPayloads(final int count)
    {
        for (int i = 0; i < count; i++)
        {
            write(1, MAX_PAYLOAD);
        }
    }

    private static void tryWriteRecords(final ByteRingBuffer ringBuffer, final int count)
    {
        for (int i = 0; i < count; i++)
        {
            assertTrue(ringBuffer.write(1, new byte[8], 0, 8));
        }
    }

    private static byte[] bytes(final String payload)
    {
        return payload.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Blocks the first set until resumed, pausing a registration before the sequence is installed.
     */
    private static final class PausingSequence extends Sequence
    {
        private final CountDownLatch firstSet = new CountDownLatch(1);
        private final CountDownLatch resume = new CountDownLatch(1);

        @Override
        public void set(final long value)
        {
            super.set(value);
            if (firstSet.getCount() > 0)
            {
                firstSet.countDown();
                try
                {
                    assertTrue(resume.await(2, TimeUnit.SECONDS));
                }
                catch (final InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            }
        }

        boolean awaitFirstSet(final long timeout, final TimeUnit unit) throws InterruptedException
        {
            return firstSet.await(timeout, unit);
        }

        void resume()
        {
            resume.countDown();
        }
    }

    /**
     * Waits at a barrier on the first set, so two registrations initialise their sequences together.
     */
    private static final class RendezvousSequence extends Sequence
    {
        private final CyclicBarrier barrier;
        private boolean arrived;

        RendezvousSequence(final CyclicBarrier barrier)
        {
            this.barrier = barrier;
        }

        @Override
        public void set(final long value)
        {
            super.set(value);
            if (!arrived)
            {
                arrived = true;
                try
                {
                    barrier.await(2, TimeUnit.SECONDS);
                }
                catch (final Exception e)
                {
                    throw new AssertionError(e);
                }
            }
        }
    }
}
