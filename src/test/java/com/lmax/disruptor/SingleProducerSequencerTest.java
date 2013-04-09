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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.lmax.disruptor.util.DaemonThreadFactory;

@RunWith(JMock.class)
public final class SingleProducerSequencerTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private static final int BUFFER_SIZE = 4;

    private final SingleProducerSequencer sequencer = new SingleProducerSequencer(BUFFER_SIZE, new SleepingWaitStrategy());
    private final Sequence cursor = new Sequence();
    private final Mockery mockery = new Mockery();

    @Test
    public void shouldStartWithInitialValue()
    {
        assertEquals(0, sequencer.next());
    }

    @Test
    public void shouldBatchClaim()
    {
        assertEquals(3, sequencer.next(4));
    }
    
    @Test
    public void shouldIndicateAvailableCapacity()
    {
        assertTrue(sequencer.hasAvailableCapacity(1));
    }

    @Test
    public void voidSthouldIndicateNoAvailableCapacity()
    {
        sequencer.addGatingSequences(cursor);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        assertFalse(sequencer.hasAvailableCapacity(1));
    }

    @Test
    public void shouldHoldUpPublisherWhenBufferIsFull()
        throws InterruptedException
    {
        sequencer.addGatingSequences(cursor);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

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

                long next = sequencer.next();
                cursor.set(next);

                doneLatch.countDown();
            }
        });

        waitingLatch.await();
        assertThat(sequencer.getNextValue(), is(expectedFullSequence));

        cursor.set(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertThat(sequencer.getNextValue(), is(expectedFullSequence + 1L));
    }

    @Test(expected = InsufficientCapacityException.class)
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull() throws Exception
    {
        sequencer.addGatingSequences(cursor);
        for (int i = 0; i < 4; i++)
        {
            sequencer.next();
        }
        sequencer.tryNext();
    }

    @Test
    public void shouldCalculateRemainingCapacity() throws Exception
    {
        sequencer.addGatingSequences(cursor);
        
        assertThat(sequencer.remainingCapacity(), is(4L));
        sequencer.next();
        assertThat(sequencer.remainingCapacity(), is(3L));
        sequencer.next();
        assertThat(sequencer.remainingCapacity(), is(2L));
        sequencer.next();
        assertThat(sequencer.remainingCapacity(), is(1L));
    }

    @Test
    public void shouldNotBeAvailableUntilPublished() throws Exception
    {
        assertThat(sequencer.isAvailable(0), is(false));
        sequencer.publish(5);
        
        for (int i = 0; i <= 5; i++)
        {
            assertThat(sequencer.isAvailable(i), is(true));
        }
        
        assertThat(sequencer.isAvailable(6), is(false));
    }

    
    @Test
    public void shouldNotifyWaitStrategyOnPublish() throws Exception
    {
        final WaitStrategy waitStrategy = mockery.mock(WaitStrategy.class);
        SingleProducerSequencer sequencer = new SingleProducerSequencer(1024, waitStrategy);
        
        mockery.checking(new Expectations()
        {
            {
                one(waitStrategy).signalAllWhenBlocking();
            }
        });
        
        sequencer.publish(0);
    }
}
