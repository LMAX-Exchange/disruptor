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

import com.lmax.disruptor.support.DaemonThreadFactory;

public final class SingleProducerSequencerTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private static final int BUFFER_SIZE = 4;

    private final Sequencer sequencer = new SingleProducerSequencer(BUFFER_SIZE, new SleepingWaitStrategy());

    private final Sequence gatingSequence = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);

    public SingleProducerSequencerTest()
    {
        sequencer.setGatingSequences(gatingSequence);
    }

    @Test
    public void shouldStartWithInitialValue()
    {
        assertEquals(SingleProducerSequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
    }

    @Test
    public void shouldGetPublishFirstSequence()
    {
        final long sequence = sequencer.next();
        assertEquals(SingleProducerSequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
        assertEquals(sequence, 0L);

        sequencer.publish(sequence);
        assertEquals(sequence, sequencer.getCursor());
    }

    @Test
    public void shouldIndicateAvailableCapacity()
    {
        assertTrue(sequencer.hasAvailableCapacity(1));
    }

    @Test
    public void voidShouldIndicateNoAvailableCapacity()
    {
        fillBuffer();

        assertFalse(sequencer.hasAvailableCapacity(1));
    }

    @Test
    public void shouldForceClaimSequence()
    {
        final long claimSequence = 3L;

        final long sequence = sequencer.claim(claimSequence);
        assertEquals(SingleProducerSequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
        assertEquals(sequence, claimSequence);

        sequencer.forcePublish(sequence);
        assertEquals(claimSequence, sequencer.getCursor());
    }

    @Test
    public void shouldWaitOnSequence()
        throws AlertException, InterruptedException
    {
        final SequenceBarrier barrier = sequencer.newBarrier();
        final long sequence = sequencer.next();
        sequencer.publish(sequence);

        assertEquals(sequence, barrier.waitFor(sequence));
    }

    @Test
    public void shouldWaitOnSequenceShowingBatchingEffect()
        throws AlertException, InterruptedException
    {
        final SequenceBarrier barrier = sequencer.newBarrier();
        sequencer.publish(sequencer.next());
        sequencer.publish(sequencer.next());

        final long sequence = sequencer.next();
        sequencer.publish(sequence);

        assertEquals(sequence, barrier.waitFor(SingleProducerSequencer.INITIAL_CURSOR_VALUE + 1L));
    }

    @Test
    public void shouldSignalWaitingProcessorWhenSequenceIsPublished()
        throws InterruptedException
    {
        final SequenceBarrier barrier = sequencer.newBarrier();
        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final long expectedSequence = SingleProducerSequencer.INITIAL_CURSOR_VALUE + 1L;

        EXECUTOR.submit(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    waitingLatch.countDown();
                    assertEquals(expectedSequence, barrier.waitFor(expectedSequence));
                }
                catch (Exception e)
                {
                    // will not happen
                }

                gatingSequence.set(expectedSequence);
                doneLatch.countDown();
            }
        });

        waitingLatch.await();
        assertEquals(gatingSequence.get(), SingleProducerSequencer.INITIAL_CURSOR_VALUE);

        sequencer.publish(sequencer.next());

        doneLatch.await();
        assertEquals(gatingSequence.get(), expectedSequence);
    }

    @Test
    public void shouldHoldUpPublisherWhenBufferIsFull()
        throws InterruptedException
    {
        fillBuffer();

        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final long expectedFullSequence = SingleProducerSequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        assertEquals(sequencer.getCursor(), expectedFullSequence);

        EXECUTOR.submit(new Runnable()
        {
            @Override
            public void run()
            {
                waitingLatch.countDown();

                sequencer.publish(sequencer.next());

                doneLatch.countDown();
            }
        });

        waitingLatch.await();
        assertEquals(sequencer.getCursor(), expectedFullSequence);

        gatingSequence.set(SingleProducerSequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertEquals(sequencer.getCursor(), expectedFullSequence + 1L);
    }
    
    @Test(expected = InsufficientCapacityException.class)
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull() throws Exception
    {
        for (int i = 0; i < 4; i++)
        {            
            long sequence = sequencer.next();
            sequencer.publish(sequence);
        }
        sequencer.tryNext();
    }
    
    @Test
    public void shouldCalculateRemainingCapacity() throws Exception
    {
        assertThat(sequencer.remainingCapacity(), is(4L));
        sequencer.publish(sequencer.next());
        assertThat(sequencer.remainingCapacity(), is(3L));
        sequencer.publish(sequencer.next());
        assertThat(sequencer.remainingCapacity(), is(2L));
        sequencer.publish(sequencer.next());
        assertThat(sequencer.remainingCapacity(), is(1L));
    }
    
    private void fillBuffer()
    {
        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            final long sequence = sequencer.next();
            sequencer.publish(sequence);
        }
    }
}
