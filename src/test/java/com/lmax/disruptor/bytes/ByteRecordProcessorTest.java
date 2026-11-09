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
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.SequenceBarrier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ByteRecordProcessorTest
{
    private static final String MAX_PAYLOAD = "0123456789abcdefghijklmn";

    private final ByteRingBuffer ringBuffer = new ByteRingBuffer(256, new BusySpinWaitStrategy());
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

    @Test
    public void shouldUpdateSequenceAfterProcessingRecords() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(3);
        final ByteRecordProcessor processor = new ByteRecordProcessor(ringBuffer, sequenceBarrier, new LatchRecordHandler(latch));
        ringBuffer.addGatingSequences(processor.getSequence());

        write(1, "a");
        write(2, "b");
        write(3, "c");

        final Thread thread = new Thread(processor);
        thread.start();

        assertTrue(latch.await(2, TimeUnit.SECONDS));

        processor.halt();
        thread.join();

        assertEquals(ringBuffer.getCursor(), processor.getSequence().get());
    }

    @Test
    public void shouldPassRecordToHandler()
    {
        final RecordingHandler handler = new RecordingHandler();
        handler.haltOnType = 8;
        final ByteRecordProcessor processor = newProcessor(handler);

        write(7, "hello");
        write(8, "world!");
        processor.run();

        assertEquals(List.of(7, 8), handler.typeIds);
        assertEquals(List.of("hello", "world!"), handler.payloads);
        assertEquals(List.of(0L, 16L), handler.positions);
        assertEquals(List.of(false, true), handler.endOfBatches);
    }

    @Test
    public void shouldSkipPaddingRecords()
    {
        for (int i = 0; i < 7; i++)
        {
            write(1, MAX_PAYLOAD);
        }
        write(1, "abcdefgh");
        final RecordingHandler handler = new RecordingHandler();
        handler.haltOnType = 9;
        final ByteRecordProcessor processor = newProcessor(handler);

        write(9, MAX_PAYLOAD);
        processor.run();

        assertEquals(List.of(9), handler.typeIds);
        assertEquals(List.of(256L), handler.positions);
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException() throws Exception
    {
        final CountDownLatch exceptionLatch = new CountDownLatch(1);
        final RecordingHandler handler = new RecordingHandler();
        handler.failOnType = 1;
        final ByteRecordProcessor processor = newProcessor(handler);
        final RecordingExceptionHandler exceptionHandler = new RecordingExceptionHandler(exceptionLatch::countDown);
        processor.setExceptionHandler(exceptionHandler);

        final Thread thread = new Thread(processor);
        thread.start();

        write(1, "a");

        assertTrue(exceptionLatch.await(2, TimeUnit.SECONDS));

        processor.halt();
        thread.join();

        assertSame(handler.failure, exceptionHandler.lastException);
        assertEquals(0L, exceptionHandler.lastPosition);
        assertNull(exceptionHandler.lastEvent);
    }

    @Test
    public void shouldPublishProgressPastFailedRecordBeforeContinuing()
    {
        final RecordingHandler handler = new RecordingHandler();
        handler.failOnType = 2;
        handler.haltOnType = 3;
        final ByteRecordProcessor processor = newProcessor(handler);
        processor.setExceptionHandler(new RecordingExceptionHandler(() ->
        {
        }));

        write(1, "a");          // 0..16
        write(2, "abcdefghi");  // 16..40
        write(3, "abcdefgh");   // 40..56
        processor.run();

        assertEquals(List.of(0L, 0L, 40L), handler.sequencesSeen);
    }

    @Test
    public void shouldHaltBeforeRemainingRecordsWhenExceptionHandlerHalts()
    {
        final RecordingHandler handler = new RecordingHandler();
        handler.failOnType = 2;
        final ByteRecordProcessor processor = newProcessor(handler);
        processor.setExceptionHandler(new RecordingExceptionHandler(processor::halt));

        write(1, "a");
        write(2, "b");
        write(3, "c");
        processor.run();

        assertEquals(List.of(1, 2), handler.typeIds);
    }

    @Test
    public void shouldNotAcknowledgeFailedBatchWhenExceptionHandlerThrows()
    {
        final RuntimeException stop = new RuntimeException("stop processing");
        final RecordingHandler handler = new RecordingHandler();
        handler.failOnType = 2;
        final ByteRecordProcessor processor = newProcessor(handler);
        processor.setExceptionHandler(new RecordingExceptionHandler(() ->
        {
            throw stop;
        }));

        write(1, "a");
        write(2, "b");

        assertSame(stop, assertThrows(RuntimeException.class, processor::run));
        assertEquals(0L, processor.getSequence().get());
    }

    @Test
    public void shouldThrowExceptionWhenRunWhileRunning() throws Exception
    {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final ByteRecordProcessor processor = newProcessor(new BlockingRecordHandler(entered, release));

        write(1, "a");
        final Thread thread = new Thread(processor);
        thread.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        assertThrows(IllegalStateException.class, processor::run);

        processor.halt();
        release.countDown();
        thread.join();
    }

    @Test
    public void shouldNotTakeOverRunningProcessorWhenRunWhileHalted() throws Exception
    {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final BlockingRecordHandler handler = new BlockingRecordHandler(entered, release);
        final ByteRecordProcessor processor = newProcessor(handler);

        write(1, "a");
        final Thread thread = new Thread(processor);
        thread.start();
        assertTrue(entered.await(2, TimeUnit.SECONDS));

        processor.halt();
        processor.run();
        processor.run();

        assertEquals(1, handler.count);

        release.countDown();
        thread.join();
    }

    @Test
    public void shouldAlwaysHalt() throws InterruptedException
    {
        for (int i = 0; i < 1000; i++)
        {
            final ByteRecordProcessor processor = new ByteRecordProcessor(ringBuffer, ringBuffer.newBarrier(), new LatchRecordHandler(new CountDownLatch(0)));
            final Thread thread = new Thread(processor);
            thread.start();
            if (i % 2 == 1)
            {
                Thread.yield();
            }
            processor.halt();

            thread.join(2000);
            assertFalse(thread.isAlive());
        }
    }

    @Test
    public void shouldRestartAfterHalting()
    {
        final RecordingHandler handler = new RecordingHandler();
        handler.haltOnType = 1;
        final ByteRecordProcessor processor = newProcessor(handler);

        write(1, "a");
        processor.run();
        write(1, "b");
        processor.run();

        assertEquals(List.of("a", "b"), handler.payloads);
    }

    @Test
    public void shouldNotProcessRecordsWhenHaltedBeforeStarting()
    {
        final RecordingHandler handler = new RecordingHandler();
        handler.haltOnType = 1;
        final ByteRecordProcessor processor = newProcessor(handler);

        write(1, "a");
        processor.halt();
        processor.run();
        processor.run();

        assertTrue(handler.typeIds.isEmpty());
        assertTrue(processor.isRunning());
    }

    private ByteRecordProcessor newProcessor(final RecordingHandler handler)
    {
        final ByteRecordProcessor processor = newProcessor((ByteRecordHandler) handler);
        handler.processor = processor;
        return processor;
    }

    private ByteRecordProcessor newProcessor(final ByteRecordHandler handler)
    {
        final ByteRecordProcessor processor = new ByteRecordProcessor(ringBuffer, sequenceBarrier, handler);
        ringBuffer.addGatingSequences(processor.getSequence());
        return processor;
    }

    private void write(final int typeId, final String payload)
    {
        final byte[] bytes = payload.getBytes(StandardCharsets.US_ASCII);
        assertTrue(ringBuffer.write(typeId, bytes, 0, bytes.length));
    }

    /**
     * Records each record it's given, and can fail on, or halt its processor after, a type id.
     */
    private static final class RecordingHandler implements ByteRecordHandler
    {
        final RuntimeException failure = new RuntimeException("record failure");
        final List<Integer> typeIds = new ArrayList<>();
        final List<String> payloads = new ArrayList<>();
        final List<Long> positions = new ArrayList<>();
        final List<Boolean> endOfBatches = new ArrayList<>();
        final List<Long> sequencesSeen = new ArrayList<>();
        int failOnType = -1;
        int haltOnType = -1;
        ByteRecordProcessor processor;

        @Override
        public void onRecord(
            final byte[] buffer, final int offset, final int length, final int typeId, final long position,
            final boolean endOfBatch)
        {
            typeIds.add(typeId);
            payloads.add(new String(buffer, offset, length, StandardCharsets.US_ASCII));
            positions.add(position);
            endOfBatches.add(endOfBatch);
            sequencesSeen.add(processor.getSequence().get());
            if (typeId == failOnType)
            {
                throw failure;
            }
            if (typeId == haltOnType)
            {
                processor.halt();
            }
        }
    }

    private static final class LatchRecordHandler implements ByteRecordHandler
    {
        private final CountDownLatch latch;

        LatchRecordHandler(final CountDownLatch latch)
        {
            this.latch = latch;
        }

        @Override
        public void onRecord(
            final byte[] buffer, final int offset, final int length, final int typeId, final long position,
            final boolean endOfBatch)
        {
            latch.countDown();
        }
    }

    private static final class BlockingRecordHandler implements ByteRecordHandler
    {
        private final CountDownLatch entered;
        private final CountDownLatch release;
        int count;

        BlockingRecordHandler(final CountDownLatch entered, final CountDownLatch release)
        {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public void onRecord(
            final byte[] buffer, final int offset, final int length, final int typeId, final long position,
            final boolean endOfBatch) throws Exception
        {
            count++;
            entered.countDown();
            assertTrue(release.await(2, TimeUnit.SECONDS));
        }
    }

    private static final class RecordingExceptionHandler implements ExceptionHandler<Object>
    {
        private final Runnable action;
        Throwable lastException;
        long lastPosition;
        Object lastEvent;

        RecordingExceptionHandler(final Runnable action)
        {
            this.action = action;
        }

        @Override
        public void handleEventException(final Throwable ex, final long sequence, final Object event)
        {
            lastException = ex;
            lastPosition = sequence;
            lastEvent = event;
            action.run();
        }

        @Override
        public void handleOnStartException(final Throwable ex)
        {
        }

        @Override
        public void handleOnShutdownException(final Throwable ex)
        {
        }
    }
}
