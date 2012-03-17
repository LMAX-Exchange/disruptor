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

import com.lmax.disruptor.support.DaemonThreadFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public final class SequencerTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new DaemonThreadFactory());
    private static final int BUFFER_SIZE = 4;

    private final Sequencer sequencer = new Sequencer(new SingleThreadedClaimStrategy(BUFFER_SIZE), new SleepingWaitStrategy());

    private final Sequence gatingSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    public SequencerTest()
    {
        sequencer.setGatingSequences(gatingSequence);
    }

    @Test
    public void shouldStartWithInitialValue()
    {
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
    }

    @Test
    public void shouldGetPublishFirstSequence()
    {
        final long sequence = sequencer.next();
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
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
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
        assertEquals(sequence, claimSequence);

        sequencer.forcePublish(sequence);
        assertEquals(claimSequence, sequencer.getCursor());
    }

    @Test
    public void shouldPublishSequenceBatch()
    {
        final int batchSize = 3;
        BatchDescriptor batchDescriptor = new BatchDescriptor(batchSize);

        batchDescriptor = sequencer.next(batchDescriptor);
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE, sequencer.getCursor());
        assertEquals(batchDescriptor.getEnd(), Sequencer.INITIAL_CURSOR_VALUE + batchSize);
        assertEquals(batchDescriptor.getSize(), batchSize);

        sequencer.publish(batchDescriptor);
        assertEquals(sequencer.getCursor(), Sequencer.INITIAL_CURSOR_VALUE + batchSize);
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

        assertEquals(sequence, barrier.waitFor(Sequencer.INITIAL_CURSOR_VALUE + 1L));
    }

    @Test
    public void shouldSignalWaitingProcessorWhenSequenceIsPublished()
        throws InterruptedException
    {
        final SequenceBarrier barrier = sequencer.newBarrier();
        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);
        final long expectedSequence = Sequencer.INITIAL_CURSOR_VALUE + 1L;

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
        assertEquals(gatingSequence.get(), Sequencer.INITIAL_CURSOR_VALUE);

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

        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
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

        gatingSequence.set(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertEquals(sequencer.getCursor(), expectedFullSequence + 1L);
    }

    @Test
    public void shouldNotTimeoutWhenBufferHasSufficientCapacity() throws TimeoutException
    {
        long sequence = sequencer.next(1000L, TimeUnit.MILLISECONDS);
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE + 1L, sequence);
    }

    @Test(expected = TimeoutException.class)
    public void shouldThrowExceptionDueToTimeoutWhenInsufficientCapacity() throws TimeoutException
    {
        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        sequencer.claim(expectedFullSequence);
        sequencer.publish(expectedFullSequence);

        sequencer.next(1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    public void shouldNotTimeoutWhenBufferHasSufficientCapacityForBatch() throws TimeoutException
    {
        final int batchSize = 3;
        BatchDescriptor batchDescriptor = sequencer.newBatchDescriptor(batchSize);

        batchDescriptor = sequencer.next(batchDescriptor, 1000L, TimeUnit.MILLISECONDS);
        assertEquals(Sequencer.INITIAL_CURSOR_VALUE + batchSize, batchDescriptor.getEnd());
    }

    @Test(expected = TimeoutException.class)
    public void shouldThrowExceptionDueToTimeoutWhenInsufficientCapacityForBatch() throws TimeoutException
    {
        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        sequencer.claim(expectedFullSequence);
        sequencer.publish(expectedFullSequence);

        final int batchSize = 3;
        BatchDescriptor batchDescriptor = sequencer.newBatchDescriptor(batchSize);

        sequencer.next(batchDescriptor, 1000L, TimeUnit.MILLISECONDS);
    }
    
    @Test(expected = InsufficientCapacityException.class)
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull() throws Exception
    {
        sequencer.tryNext(5);
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
