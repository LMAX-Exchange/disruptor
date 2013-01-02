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

import static com.lmax.disruptor.RingBuffer.createMultiProducer;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.support.TestWaiter;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class RingBufferTest
{
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final RingBuffer<StubEvent> ringBuffer = RingBuffer.createMultiProducer(StubEvent.EVENT_FACTORY, 32);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    {
        ringBuffer.addGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());
    }

    @Test
    public void shouldClaimAndGet() throws Exception
    {
        assertEquals(SingleProducerSequencer.INITIAL_CURSOR_VALUE, ringBuffer.getCursor());

        StubEvent expectedEvent = new StubEvent(2701);

        long claimSequence = ringBuffer.next();
        StubEvent oldEvent = ringBuffer.getPreallocated(claimSequence);
        oldEvent.copy(expectedEvent);
        ringBuffer.publish(claimSequence);

        long sequence = sequenceBarrier.waitFor(0);
        assertEquals(0, sequence);

        StubEvent event = ringBuffer.getPublished(sequence);
        assertEquals(expectedEvent, event);

        assertEquals(0L, ringBuffer.getCursor());
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        Future<List<StubEvent>> messages = getMessages(0, 0);

        StubEvent expectedEvent = new StubEvent(2701);

        long sequence = ringBuffer.next();
        StubEvent oldEvent = ringBuffer.getPreallocated(sequence);
        oldEvent.copy(expectedEvent);
        ringBuffer.publish(sequence);

        assertEquals(expectedEvent, messages.get().get(0));
    }

    @Test
    public void shouldClaimAndGetMultipleMessages() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        for (int i = 0; i < numMessages; i++)
        {
            long sequence = ringBuffer.next();
            StubEvent event = ringBuffer.getPreallocated(sequence);
            event.setValue(i);
            ringBuffer.publish(sequence);
        }

        int expectedSequence = numMessages - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = 0; i < numMessages; i++)
        {
            assertEquals(i, ringBuffer.getPublished(i).getValue());
        }
    }

    @Test
    public void shouldWrap() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        int offset = 1000;
        for (int i = 0; i < numMessages + offset; i++)
        {
            long sequence = ringBuffer.next();
            StubEvent event = ringBuffer.getPreallocated(sequence);
            event.setValue(i);
            ringBuffer.publish(sequence);
        }

        int expectedSequence = numMessages + offset - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = offset; i < numMessages + offset; i++)
        {
            assertEquals(i, ringBuffer.getPublished(i).getValue());
        }
    }

    @Test
    public void shouldPreventWrapping() throws Exception
    {
        Sequence sequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, 4);
        ringBuffer.addGatingSequences(sequence);

        ringBuffer.publishEvent(StubEvent.TRANSLATOR, 0, "0");
        ringBuffer.publishEvent(StubEvent.TRANSLATOR, 1, "1");
        ringBuffer.publishEvent(StubEvent.TRANSLATOR, 2, "2");
        ringBuffer.publishEvent(StubEvent.TRANSLATOR, 3, "3");

        assertFalse(ringBuffer.tryPublishEvent(StubEvent.TRANSLATOR, 1, 3, "3"));
    }
    
    @Test
    public void shouldThrowExceptionIfBufferIsFull() throws Exception
    {
        ringBuffer.addGatingSequences(new Sequence(ringBuffer.getBufferSize()));
        
        try
        {
            for (int i = 0; i < ringBuffer.getBufferSize(); i++)
            {
                ringBuffer.publish(ringBuffer.tryNext());
            }
        }
        catch (Exception e)
        {
            fail("Should not of thrown exception");
        }
        
        try
        {   
            ringBuffer.tryNext();
            fail("Exception should have been thrown");
        }
        catch (InsufficientCapacityException e)
        {
        }
    }

    @Test
    public void shouldPreventPublishersOvertakingEventProcessorWrapPoint() throws InterruptedException
    {
        final int ringBufferSize = 4;
        final CountDownLatch latch = new CountDownLatch(ringBufferSize);
        final AtomicBoolean publisherComplete = new AtomicBoolean(false);
        final RingBuffer<StubEvent> ringBuffer = createMultiProducer(StubEvent.EVENT_FACTORY, ringBufferSize);
        final TestEventProcessor processor = new TestEventProcessor(ringBuffer.newBarrier());
        ringBuffer.addGatingSequences(processor.getSequence());

        Thread thread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                for (int i = 0; i <= ringBufferSize; i++)
                {
                    long sequence = ringBuffer.next();
                    StubEvent event = ringBuffer.getPreallocated(sequence);
                    event.setValue(i);
                    ringBuffer.publish(sequence);
                    latch.countDown();
                }

                publisherComplete.set(true);
            }
        });
        thread.start();

        latch.await();
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(ringBufferSize - 1)));
        assertFalse(publisherComplete.get());

        processor.run();
        thread.join();

        assertTrue(publisherComplete.get());
    }
    
    @Test
    public void shouldPublishEvent() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        ringBuffer.publishEvent(new EventTranslator<Object[]>()
        {
            public void translateTo(final Object[] event, long sequence)
            {
                event[0] = sequence;
            }
        });
        
        assertThat(ringBuffer.getPublished(0)[0], is((Object) 0L));
    }

    @Test
    public void shouldPublishEventOneArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = 
                new EventTranslatorOneArg<Object[], String>()
        {
            @Override
            public void translateTo(Object[] event, long sequence, String arg0)
            {
                event[0] = arg0 + sequence;
            }
        };
        
        ringBuffer.publishEvent(translator, "Foo");
        ringBuffer.tryPublishEvent(translator, 1, "Foo");
        
        assertThat(ringBuffer.getPublished(0)[0], is((Object) "Foo0"));
        assertThat(ringBuffer.getPublished(1)[0], is((Object) "Foo1"));
    }

    @Test
    public void shouldPublishEventTwoArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = 
                new EventTranslatorTwoArg<Object[], String, String>()
        {
            @Override
            public void translateTo(Object[] event, long sequence, String arg0, String arg1)
            {
                event[0] = arg0 + arg1 + sequence;
            }
        };
        
        ringBuffer.publishEvent(translator, "Foo", "Bar");
        ringBuffer.tryPublishEvent(translator, 1, "Foo", "Bar");
        
        assertThat(ringBuffer.getPublished(0)[0], is((Object) "FooBar0"));
        assertThat(ringBuffer.getPublished(1)[0], is((Object) "FooBar1"));
    }

    @Test
    public void shouldPublishEventThreeArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = 
                new EventTranslatorThreeArg<Object[], String, String, String>()
        {
            @Override
            public void translateTo(Object[] event, long sequence, String arg0, String arg1, String arg2)
            {
                event[0] = arg0 + arg1 + arg2 + sequence;
            }
        };
        
        ringBuffer.publishEvent(translator, "Foo", "Bar", "Baz");
        ringBuffer.tryPublishEvent(translator, 1, "Foo", "Bar", "Baz");
        
        assertThat(ringBuffer.getPublished(0)[0], is((Object) "FooBarBaz0"));
        assertThat(ringBuffer.getPublished(1)[0], is((Object) "FooBarBaz1"));
    }

    @Test
    public void shouldPublishEventVarArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = 
                new EventTranslatorVararg<Object[]>()
        {
            @Override
            public void translateTo(Object[] event, long sequence, Object...args)
            {
                event[0] = (String)args[0] + args[1] + args[2] + args[3] + sequence;
            }
        };
        
        ringBuffer.publishEvent(translator, "Foo", "Bar", "Baz", "Bam");
        ringBuffer.tryPublishEvent(translator, 1, "Foo", "Bar", "Baz", "Bam");
        
        assertThat(ringBuffer.getPublished(0)[0], is((Object) "FooBarBazBam0"));
        assertThat(ringBuffer.getPublished(1)[0], is((Object) "FooBarBazBam1"));
    }
    
    @Test
    public void shouldAddAndRemoveSequences() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 16);

        Sequence sequenceThree = new Sequence(-1);
        Sequence sequenceSeven = new Sequence(-1);
        ringBuffer.addGatingSequences(sequenceThree, sequenceSeven);
        
        for (int i = 0; i < 10; i++)
        {
            ringBuffer.publish(ringBuffer.next());
        }
        
        sequenceThree.set(3);
        sequenceSeven.set(7);
        
        assertThat(ringBuffer.getMinimumGatingSequence(), is(3L));
        assertTrue(ringBuffer.removeGatingSequence(sequenceThree));
        assertThat(ringBuffer.getMinimumGatingSequence(), is(7L));
    }
    
    @Test
    public void shouldHandleResetToAndNotWrapUnecessarilySingleProducer() throws Exception
    {
        assertHandleResetAndNotWrap(RingBuffer.createSingleProducer(StubEvent.EVENT_FACTORY, 4));
    }
    
    @Test
    public void shouldHandleResetToAndNotWrapUnecessarilyMultiProducer() throws Exception
    {
        assertHandleResetAndNotWrap(RingBuffer.createMultiProducer(StubEvent.EVENT_FACTORY, 4));
    }

    private void assertHandleResetAndNotWrap(RingBuffer<StubEvent> rb)
    {
        Sequence sequence = new Sequence();
        rb.addGatingSequences(sequence);
        
        for (int i = 0; i < 128; i++)
        {
            rb.publish(rb.next());
            sequence.incrementAndGet();
        }
        
        assertThat(rb.getCursor(), is(127L));
        
        rb.resetTo(31);
        sequence.set(31);
        
        for (int i = 0; i < 4; i++)
        {
            rb.publish(rb.next());
        }
        
        assertThat(rb.hasAvailableCapacity(1), is(false));
    }
    
    private Future<List<StubEvent>> getMessages(final long initial, final long toWaitFor)
        throws InterruptedException, BrokenBarrierException
    {
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        final Future<List<StubEvent>> f = EXECUTOR.submit(new TestWaiter(cyclicBarrier, sequenceBarrier, ringBuffer, initial, toWaitFor));

        cyclicBarrier.await();

        return f;
    }

    private static final class TestEventProcessor implements EventProcessor
    {
        private final SequenceBarrier sequenceBarrier;
        private final Sequence sequence = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);

        public TestEventProcessor(final SequenceBarrier sequenceBarrier)
        {
            this.sequenceBarrier = sequenceBarrier;
        }

        @Override
        public Sequence getSequence()
        {
            return sequence;
        }

        @Override
        public void halt()
        {
        }

        @Override
        public void run()
        {
            try
            {
                sequenceBarrier.waitFor(0L);
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }

            sequence.set(sequence.get() + 1L);
        }
    }
    
    private static class ArrayFactory implements EventFactory<Object[]>
    {
        private final int size;

        public ArrayFactory(int size)
        {
            this.size = size;
        }
        
        @Override
        public Object[] newInstance()
        {
            return new Object[size];
        }
    }
}
