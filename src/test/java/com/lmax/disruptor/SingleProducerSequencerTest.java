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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;

import com.lmax.disruptor.util.DaemonThreadFactory;

public final class SingleProducerSequencerTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private static final int BUFFER_SIZE = 4;

    private final SingleProducerSequencer sequencer = new SingleProducerSequencer(BUFFER_SIZE, new SleepingWaitStrategy());
    private final Sequence cursor = new Sequence();
    private final Sequence[] gatingSequences = { new Sequence(Sequencer.INITIAL_CURSOR_VALUE) };

    public SingleProducerSequencerTest()
    {
    }

    @Test
    public void shouldStartWithInitialValue()
    {
        assertEquals(0, sequencer.next(gatingSequences));
    }

    @Test
    public void shouldIndicateAvailableCapacity()
    {
        assertTrue(sequencer.hasAvailableCapacity(gatingSequences, 1));
    }

    @Test
    public void voidSthouldIndicateNoAvailableCapacity()
    {
        fillBuffer();

        assertFalse(sequencer.hasAvailableCapacity(gatingSequences, 1));
    }

    @Test
    public void shouldHoldUpPublisherWhenBufferIsFull()
        throws InterruptedException
    {
        fillBuffer();

        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        assertThat(sequencer.getNextValue(), is(expectedFullSequence));

        EXECUTOR.submit(new Runnable()
        {
            @Override
            public void run()
            {
                waitingLatch.countDown();

                long next = sequencer.next(gatingSequences);
                cursor.setOrdered(next);

                doneLatch.countDown();
            }
        });

        waitingLatch.await();
        assertThat(sequencer.getNextValue(), is(expectedFullSequence));

        gatingSequences[0].setOrdered(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertThat(sequencer.getNextValue(), is(expectedFullSequence + 1L));
    }

    @Test(expected = InsufficientCapacityException.class)
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull() throws Exception
    {
        for (int i = 0; i < 4; i++)
        {
            sequencer.next(gatingSequences);
        }
        sequencer.tryNext(gatingSequences);
    }

    @Test
    public void shouldCalculateRemainingCapacity() throws Exception
    {
        assertThat(sequencer.remainingCapacity(gatingSequences), is(4L));
        sequencer.next(gatingSequences);
        assertThat(sequencer.remainingCapacity(gatingSequences), is(3L));
        sequencer.next(gatingSequences);
        assertThat(sequencer.remainingCapacity(gatingSequences), is(2L));
        sequencer.next(gatingSequences);
        assertThat(sequencer.remainingCapacity(gatingSequences), is(1L));
    }

    private void fillBuffer()
    {
        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            long next = sequencer.next(gatingSequences);
            cursor.setOrdered(next);
        }
    }
}
