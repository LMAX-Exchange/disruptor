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

import com.lmax.disruptor.support.PerfTestUtil;
import com.lmax.disruptor.util.PaddedLong;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This performance test illustrates direct use of the {@link Sequencer} without requiring a {@link RingBuffer}.
 * <p>
 * The {@link Sequencer} can be used to sequence access to any data structure as can been seen from the use of the "values" array below.
 */
public class OnePublisherToOneProcessorUniCastRawThroughputTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int BUFFER_SIZE = 1024 * 8;
    private static final int indexMask = BUFFER_SIZE - 1;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private final long expectedResult = PerfTestUtil.accumulatedAddition(ITERATIONS);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final long[] values = new long[BUFFER_SIZE];
    private final Sequencer sequencer = new Sequencer(BUFFER_SIZE, ClaimStrategy.Option.SINGLE_THREADED, WaitStrategy.Option.YIELDING);
    private final SequenceBarrier barrier = sequencer.newBarrier();
    private final RawProcessor rawProcessor = new RawProcessor(values, barrier);
    {
        sequencer.setGatingSequences(rawProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runQueuePass() throws InterruptedException
    {
        // Same expected results as UniCast scenario
        return 0L;
    }

    @Override
    protected long runDisruptorPass() throws InterruptedException
    {
        rawProcessor.reset();

        EXECUTOR.submit(rawProcessor);
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            long sequence = sequencer.next();
            values[(int)sequence & indexMask] = i;
            sequencer.publish(sequence);
        }

        final long expectedSequence = sequencer.getCursor();
        while (rawProcessor.getSequence().get() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        rawProcessor.halt();

        Assert.assertEquals(expectedResult, rawProcessor.getValue());

        return opsPerSecond;
    }

    public static final class RawProcessor implements EventProcessor
    {
        private final PaddedLong value = new PaddedLong();
        private final Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        private volatile boolean running = false;
        private final long[] values;
        private final SequenceBarrier barrier;

        public RawProcessor(final long[] values, final SequenceBarrier barrier)
        {
            this.values = values;
            this.barrier = barrier;
        }

        public void reset()
        {
            value.set(0L);
        }

        public long getValue()
        {
            return value.get();
        }

        @Override
        public Sequence getSequence()
        {
            return sequence;
        }

        @Override
        public void halt()
        {
            running = false;
            barrier.alert();
        }

        @Override
        public void run()
        {
            running = true;
            barrier.clearAlert();

            long nextSequence = sequence.get() + 1L;
            while (true)
            {
                try
                {
                    final long availableSequence = barrier.waitFor(nextSequence);
                    while (nextSequence <= availableSequence)
                    {
                        value.set(value.get() + values[(int)nextSequence & indexMask]);
                        nextSequence++;
                    }

                    sequence.set(nextSequence - 1L);
                }
                catch (final AlertException ex)
                {
                    if (!running)
                    {
                        break;
                    }
                }
                catch (Exception ex)
                {
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}
