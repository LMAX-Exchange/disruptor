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

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.ExceptionHandlers;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Convenience class for handling the batching semantics of consuming records from a {@link ByteRingBuffer}
 * and delegating the available records to a {@link ByteRecordHandler}.
 */
public final class ByteRecordProcessor implements EventProcessor
{
    private static final int IDLE = 0;
    private static final int HALTED = IDLE + 1;
    private static final int RUNNING = HALTED + 1;

    private final AtomicInteger running = new AtomicInteger(IDLE);
    private final Sequence sequence = new Sequence(0L);
    private final ByteRingBuffer ringBuffer;
    private final SequenceBarrier barrier;
    private final ByteRecordHandler handler;
    private ExceptionHandler<Object> exceptionHandler = ExceptionHandlers.defaultHandler();

    /**
     * Construct a processor that will automatically track the progress by updating its sequence when
     * the {@link ByteRecordHandler#onRecord} method returns.
     *
     * @param ringBuffer to which records are published.
     * @param barrier    on which it is waiting.
     * @param handler    is the delegate to which records are dispatched.
     */
    public ByteRecordProcessor(final ByteRingBuffer ringBuffer, final SequenceBarrier barrier, final ByteRecordHandler handler)
    {
        this.ringBuffer = ringBuffer;
        this.barrier = barrier;
        this.handler = handler;
    }

    /**
     * Set a new {@link ExceptionHandler} for handling exceptions propagated out of the {@link ByteRecordHandler}.
     * It is passed the position of the failed record and a null event.
     *
     * @param exceptionHandler to replace the existing exceptionHandler.
     */
    public void setExceptionHandler(final ExceptionHandler<Object> exceptionHandler)
    {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    @Override
    public Sequence getSequence()
    {
        return sequence;
    }

    @Override
    public void halt()
    {
        running.set(HALTED);
        barrier.alert();
    }

    @Override
    public boolean isRunning()
    {
        return running.get() != IDLE;
    }

    /**
     * It is ok to have another thread rerun this method after a halt().
     *
     * @throws IllegalStateException if this object instance is already running in a thread
     */
    @Override
    public void run()
    {
        final int witness = running.compareAndExchange(IDLE, RUNNING);
        if (witness == RUNNING)
        {
            throw new IllegalStateException("Thread is already running");
        }
        if (witness == IDLE)
        {
            barrier.clearAlert();
            try
            {
                if (running.get() == RUNNING)
                {
                    processRecords();
                }
            }
            finally
            {
                running.set(IDLE);
            }
        }
        // HALTED: the running invocation exits and resets the state
    }

    private void processRecords()
    {
        final ByteRingBuffer ring = this.ringBuffer;
        final ByteRecordHandler handler = this.handler;
        final SequenceBarrier barrier = this.barrier;
        final Sequence sequence = this.sequence;
        final byte[] buffer = ring.buffer();
        final int mask = ring.capacity() - 1;
        long next = sequence.get();

        while (true)
        {
            try
            {
                final long available = barrier.waitFor(next + 1L);
                while (next < available)
                {
                    final int index = (int) next & mask;
                    final long header = ByteRingBuffer.headerAt(buffer, index);
                    final int recordLength = (int) header;
                    final int typeId = (int) (header >>> (ByteRingBuffer.TYPE_OFFSET * Byte.SIZE));
                    final long recordEnd = next + ByteRingBuffer.align(recordLength);
                    if (typeId != ByteRingBuffer.PADDING_TYPE_ID)
                    {
                        try
                        {
                            handler.onRecord(
                                buffer,
                                index + ByteRingBuffer.HEADER_LENGTH,
                                recordLength - ByteRingBuffer.HEADER_LENGTH,
                                typeId,
                                next,
                                recordEnd == available);
                        }
                        catch (final Throwable ex)
                        {
                            exceptionHandler.handleEventException(ex, next, null);
                            next = recordEnd;
                            break;
                        }
                    }
                    next = recordEnd;
                }
                sequence.set(next);
            }
            catch (final TimeoutException e)
            {
                // Keep waiting.
            }
            catch (final AlertException e)
            {
                if (running.get() != RUNNING)
                {
                    break;
                }
            }
            catch (final InterruptedException e)
            {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
}
