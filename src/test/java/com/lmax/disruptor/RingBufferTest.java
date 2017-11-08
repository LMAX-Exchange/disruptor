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
import static com.lmax.disruptor.RingBufferEventMatcher.ringBufferWithEvents;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
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
        ringBuffer.publishEvent(StubEvent.TRANSLATOR, expectedEvent.getValue(), expectedEvent.getTestString());

        long sequence = sequenceBarrier.waitFor(0);
        assertEquals(0, sequence);

        StubEvent event = ringBuffer.get(sequence);
        assertEquals(expectedEvent, event);

        assertEquals(0L, ringBuffer.getCursor());
    }

    @Test
    public void shouldClaimAndGetInSeparateThread() throws Exception
    {
        Future<List<StubEvent>> messages = getMessages(0, 0);

        StubEvent expectedEvent = new StubEvent(2701);
        ringBuffer.publishEvent(StubEvent.TRANSLATOR, expectedEvent.getValue(), expectedEvent.getTestString());

        assertEquals(expectedEvent, messages.get().get(0));
    }

    @Test
    public void shouldClaimAndGetMultipleMessages() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        for (int i = 0; i < numMessages; i++)
        {
            ringBuffer.publishEvent(StubEvent.TRANSLATOR, i, "");
        }

        long expectedSequence = numMessages - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = 0; i < numMessages; i++)
        {
            assertEquals(i, ringBuffer.get(i).getValue());
        }
    }

    @Test
    public void shouldWrap() throws Exception
    {
        int numMessages = ringBuffer.getBufferSize();
        int offset = 1000;
        for (int i = 0; i < numMessages + offset; i++)
        {
            ringBuffer.publishEvent(StubEvent.TRANSLATOR, i, "");
        }

        long expectedSequence = numMessages + offset - 1;
        long available = sequenceBarrier.waitFor(expectedSequence);
        assertEquals(expectedSequence, available);

        for (int i = offset; i < numMessages + offset; i++)
        {
            assertEquals(i, ringBuffer.get(i).getValue());
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

        assertFalse(ringBuffer.tryPublishEvent(StubEvent.TRANSLATOR, 3, "3"));
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
        final int ringBufferSize = 16;
        final CountDownLatch latch = new CountDownLatch(ringBufferSize);
        final AtomicBoolean publisherComplete = new AtomicBoolean(false);
        final RingBuffer<StubEvent> buffer2 = createMultiProducer(StubEvent.EVENT_FACTORY, ringBufferSize);
        final TestEventProcessor processor = new TestEventProcessor(buffer2.newBarrier());
        buffer2.addGatingSequences(processor.getSequence());

        Thread thread = new Thread(
            new Runnable()
            {
                @Override
                public void run()
                {
                    for (int i = 0; i <= ringBufferSize; i++)
                    {
                        long sequence = buffer2.next();
                        StubEvent event = buffer2.get(sequence);
                        event.setValue(i);
                        buffer2.publish(sequence);
                        latch.countDown();
                    }

                    publisherComplete.set(true);
                }
            });
        thread.start();

        latch.await();
        assertThat(Long.valueOf(buffer2.getCursor()), is(Long.valueOf(ringBufferSize - 1)));
        assertFalse(publisherComplete.get());

        processor.run();
        thread.join();

        assertTrue(publisherComplete.get());
    }

    @Test
    public void shouldPublishEvent() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        final EventTranslator<Object[]> translator = new NoArgEventTranslator();

        ringBuffer.publishEvent(translator);
        ringBuffer.tryPublishEvent(translator);

        assertThat(ringBuffer, ringBufferWithEvents(0L, 1L));
    }

    @Test
    public void shouldPublishEventOneArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        ringBuffer.publishEvent(translator, "Foo");
        ringBuffer.tryPublishEvent(translator, "Foo");

        assertThat(ringBuffer, ringBufferWithEvents("Foo-0", "Foo-1"));
    }

    @Test
    public void shouldPublishEventTwoArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        ringBuffer.publishEvent(translator, "Foo", "Bar");
        ringBuffer.tryPublishEvent(translator, "Foo", "Bar");

        assertThat(ringBuffer, ringBufferWithEvents("FooBar-0", "FooBar-1"));
    }

    @Test
    public void shouldPublishEventThreeArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        ringBuffer.publishEvent(translator, "Foo", "Bar", "Baz");
        ringBuffer.tryPublishEvent(translator, "Foo", "Bar", "Baz");

        assertThat(ringBuffer, ringBufferWithEvents("FooBarBaz-0", "FooBarBaz-1"));
    }

    @Test
    public void shouldPublishEventVarArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = new VarArgEventTranslator();

        ringBuffer.publishEvent(translator, "Foo", "Bar", "Baz", "Bam");
        ringBuffer.tryPublishEvent(translator, "Foo", "Bar", "Baz", "Bam");

        assertThat(ringBuffer, ringBufferWithEvents("FooBarBazBam-0", "FooBarBazBam-1"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPublishEvents() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        final EventTranslator<Object[]> eventTranslator = new NoArgEventTranslator();
        final EventTranslator<Object[]>[] translators = new EventTranslator[]{eventTranslator, eventTranslator};

        ringBuffer.publishEvents(translators);
        assertTrue(ringBuffer.tryPublishEvents(translators));

        assertThat(ringBuffer, ringBufferWithEvents(0L, 1L, 2L, 3L));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsIfBatchIsLargerThanRingBuffer() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        final EventTranslator<Object[]> eventTranslator = new NoArgEventTranslator();
        final EventTranslator<Object[]>[] translators =
            new EventTranslator[]{eventTranslator, eventTranslator, eventTranslator, eventTranslator, eventTranslator};

        try
        {
            ringBuffer.tryPublishEvents(translators);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPublishEventsWithBatchSizeOfOne() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        final EventTranslator<Object[]> eventTranslator = new NoArgEventTranslator();
        final EventTranslator<Object[]>[] translators =
            new EventTranslator[]{eventTranslator, eventTranslator, eventTranslator};

        ringBuffer.publishEvents(translators, 0, 1);
        assertTrue(ringBuffer.tryPublishEvents(translators, 0, 1));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                is((Object) 0L), is((Object) 1L), is(nullValue()), is(
                    nullValue())));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void shouldPublishEventsWithinBatch() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        final EventTranslator<Object[]> eventTranslator = new NoArgEventTranslator();
        final EventTranslator<Object[]>[] translators =
            new EventTranslator[]{eventTranslator, eventTranslator, eventTranslator};

        ringBuffer.publishEvents(translators, 1, 2);
        assertTrue(ringBuffer.tryPublishEvents(translators, 1, 2));

        assertThat(ringBuffer, ringBufferWithEvents(0L, 1L, 2L, 3L));
    }

    @Test
    public void shouldPublishEventsOneArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        ringBuffer.publishEvents(translator, new String[]{"Foo", "Foo"});
        assertTrue(ringBuffer.tryPublishEvents(translator, new String[]{"Foo", "Foo"}));

        assertThat(ringBuffer, ringBufferWithEvents("Foo-0", "Foo-1", "Foo-2", "Foo-3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsOneArgIfBatchIsLargerThanRingBuffer() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, new String[]{"Foo", "Foo", "Foo", "Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test
    public void shouldPublishEventsOneArgBatchSizeOfOne() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        ringBuffer.publishEvents(translator, 0, 1, new String[]{"Foo", "Foo"});
        assertTrue(ringBuffer.tryPublishEvents(translator, 0, 1, new String[]{"Foo", "Foo"}));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                is((Object) "Foo-0"), is((Object) "Foo-1"), is(nullValue()), is(
                    nullValue())));
    }

    @Test
    public void shouldPublishEventsOneArgWithinBatch() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        ringBuffer.publishEvents(translator, 1, 2, new String[]{"Foo", "Foo", "Foo"});
        assertTrue(ringBuffer.tryPublishEvents(translator, 1, 2, new String[]{"Foo", "Foo", "Foo"}));

        assertThat(ringBuffer, ringBufferWithEvents("Foo-0", "Foo-1", "Foo-2", "Foo-3"));
    }

    @Test
    public void shouldPublishEventsTwoArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        ringBuffer.publishEvents(translator, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        ringBuffer.tryPublishEvents(translator, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});

        assertThat(ringBuffer, ringBufferWithEvents("FooBar-0", "FooBar-1", "FooBar-2", "FooBar-3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsITwoArgIfBatchSizeIsBiggerThanRingBuffer() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator,
                new String[]{"Foo", "Foo", "Foo", "Foo", "Foo"},
                new String[]{"Bar", "Bar", "Bar", "Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test
    public void shouldPublishEventsTwoArgWithBatchSizeOfOne() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        ringBuffer.publishEvents(translator, 0, 1, new String[]{"Foo0", "Foo1"}, new String[]{"Bar0", "Bar1"});
        ringBuffer.tryPublishEvents(translator, 0, 1, new String[]{"Foo2", "Foo3"}, new String[]{"Bar2", "Bar3"});

        assertThat(
            ringBuffer, ringBufferWithEvents(
                is((Object) "Foo0Bar0-0"), is((Object) "Foo2Bar2-1"), is(
                    nullValue()), is(nullValue())));
    }

    @Test
    public void shouldPublishEventsTwoArgWithinBatch() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        ringBuffer.publishEvents(
            translator, 1, 2, new String[]{"Foo0", "Foo1", "Foo2"}, new String[]{"Bar0", "Bar1", "Bar2"});
        ringBuffer.tryPublishEvents(
            translator, 1, 2, new String[]{"Foo3", "Foo4", "Foo5"}, new String[]{"Bar3", "Bar4", "Bar5"});

        assertThat(ringBuffer, ringBufferWithEvents("Foo1Bar1-0", "Foo2Bar2-1", "Foo4Bar4-2", "Foo5Bar5-3"));
    }

    @Test
    public void shouldPublishEventsThreeArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        ringBuffer.publishEvents(
            translator, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});
        ringBuffer.tryPublishEvents(
            translator, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});

        assertThat(ringBuffer, ringBufferWithEvents("FooBarBaz-0", "FooBarBaz-1", "FooBarBaz-2", "FooBarBaz-3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsThreeArgIfBatchIsLargerThanRingBuffer() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator,
                new String[]{"Foo", "Foo", "Foo", "Foo", "Foo"},
                new String[]{"Bar", "Bar", "Bar", "Bar", "Bar"},
                new String[]{"Baz", "Baz", "Baz", "Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test
    public void shouldPublishEventsThreeArgBatchSizeOfOne() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        ringBuffer.publishEvents(
            translator, 0, 1, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});
        ringBuffer.tryPublishEvents(
            translator, 0, 1, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});

        assertThat(
            ringBuffer, ringBufferWithEvents(
                is((Object) "FooBarBaz-0"), is((Object) "FooBarBaz-1"), is(
                    nullValue()), is(nullValue())));
    }

    @Test
    public void shouldPublishEventsThreeArgWithinBatch() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        ringBuffer.publishEvents(
            translator, 1, 2, new String[]{"Foo0", "Foo1", "Foo2"}, new String[]{"Bar0", "Bar1", "Bar2"},
            new String[]{"Baz0", "Baz1", "Baz2"}
        );
        assertTrue(
            ringBuffer.tryPublishEvents(
                translator, 1, 2, new String[]{"Foo3", "Foo4", "Foo5"}, new String[]{"Bar3", "Bar4", "Bar5"},
                new String[]{"Baz3", "Baz4", "Baz5"}));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                "Foo1Bar1Baz1-0", "Foo2Bar2Baz2-1", "Foo4Bar4Baz4-2", "Foo5Bar5Baz5-3"));
    }

    @Test
    public void shouldPublishEventsVarArg() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = new VarArgEventTranslator();

        ringBuffer.publishEvents(
            translator, new String[]{"Foo", "Bar", "Baz", "Bam"}, new String[]{"Foo", "Bar", "Baz", "Bam"});
        assertTrue(
            ringBuffer.tryPublishEvents(
                translator, new String[]{"Foo", "Bar", "Baz", "Bam"}, new String[]{"Foo", "Bar", "Baz", "Bam"}));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                "FooBarBazBam-0", "FooBarBazBam-1", "FooBarBazBam-2", "FooBarBazBam-3"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsVarArgIfBatchIsLargerThanRingBuffer() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator,
                new String[]{"Foo", "Bar", "Baz", "Bam"},
                new String[]{"Foo", "Bar", "Baz", "Bam"},
                new String[]{"Foo", "Bar", "Baz", "Bam"},
                new String[]{"Foo", "Bar", "Baz", "Bam"},
                new String[]{"Foo", "Bar", "Baz", "Bam"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test
    public void shouldPublishEventsVarArgBatchSizeOfOne() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = new VarArgEventTranslator();

        ringBuffer.publishEvents(
            translator, 0, 1, new String[]{"Foo", "Bar", "Baz", "Bam"}, new String[]{"Foo", "Bar", "Baz", "Bam"});
        assertTrue(
            ringBuffer.tryPublishEvents(
                translator, 0, 1, new String[]{"Foo", "Bar", "Baz", "Bam"}, new String[]{"Foo", "Bar", "Baz", "Bam"}));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                is((Object) "FooBarBazBam-0"), is((Object) "FooBarBazBam-1"), is(
                    nullValue()), is(nullValue())));
    }

    @Test
    public void shouldPublishEventsVarArgWithinBatch() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorVararg<Object[]> translator = new VarArgEventTranslator();

        ringBuffer.publishEvents(
            translator, 1, 2, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
            new String[]{"Foo1", "Bar1", "Baz1", "Bam1"},
            new String[]{"Foo2", "Bar2", "Baz2", "Bam2"});
        assertTrue(
            ringBuffer.tryPublishEvents(
                translator, 1, 2, new String[]{"Foo3", "Bar3", "Baz3", "Bam3"},
                new String[]{"Foo4", "Bar4", "Baz4", "Bam4"},
                new String[]{"Foo5", "Bar5", "Baz5", "Bam5"}));

        assertThat(
            ringBuffer, ringBufferWithEvents(
                "Foo1Bar1Baz1Bam1-0", "Foo2Bar2Baz2Bam2-1", "Foo4Bar4Baz4Bam4-2", "Foo5Bar5Baz5Bam5-3"));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(new EventTranslator[]{translator, translator, translator, translator}, 1, 0);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(new EventTranslator[]{translator, translator, translator, translator}, 1, 0);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(new EventTranslator[]{translator, translator, translator}, 1, 3);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(new EventTranslator[]{translator, translator, translator}, 1, 3);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(new EventTranslator[]{translator, translator, translator, translator}, 1, -1);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(new EventTranslator[]{translator, translator, translator, translator}, 1, -1);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();
        try
        {
            ringBuffer.publishEvents(new EventTranslator[]{translator, translator, translator, translator}, -1, 2);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @SuppressWarnings("unchecked")
    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslator<Object[]> translator = new NoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(new EventTranslator[]{translator, translator, translator, translator}, -1, 2);
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsOneArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, 0, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsOneArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, 1, 0, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsOneArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, 3, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsOneArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, -1, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsOneArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();
        try
        {
            ringBuffer.publishEvents(translator, -1, 2, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsOneArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, 1, 3, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsOneArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            assertFalse(ringBuffer.tryPublishEvents(translator, 1, -1, new String[]{"Foo", "Foo"}));
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsOneArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorOneArg<Object[], String> translator = new OneArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, -1, 2, new String[]{"Foo", "Foo"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsTwoArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, 0, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsTwoArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, 1, 0, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsTwoArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, 3, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsTwoArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(translator, 1, -1, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsTwoArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();
        try
        {
            ringBuffer.publishEvents(translator, -1, 2, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsTwoArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, 1, 3, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsTwoArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, 1, -1, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsTwoArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorTwoArg<Object[], String, String> translator = new TwoArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(translator, -1, 2, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsThreeArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, 0, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"},
                new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsThreeArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                    translator, 1, 0, new String[]{"Foo", "Foo"},
                    new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsThreeArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, 3, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"},
                new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsThreeArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, -1, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"},
                new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsThreeArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, -1, 2, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"},
                new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsThreeArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, 1, 3, new String[]{"Foo", "Foo"}, new String[]{"Bar", "Bar"},
                new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsThreeArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, 1, -1, new String[]{"Foo", "Foo"},
                new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsThreeArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        EventTranslatorThreeArg<Object[], String, String, String> translator = new ThreeArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, -1, 2, new String[]{"Foo", "Foo"},
                new String[]{"Bar", "Bar"}, new String[]{"Baz", "Baz"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsVarArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, 0, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsVarArgWhenBatchSizeIs0() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                    translator, 1, 0, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                    new String[]{"Foo1", "Bar1", "Baz1", "Bam1"},
                    new String[]{"Foo2", "Bar2", "Baz2", "Bam2"});
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsVarArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, 3, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsVarArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, 1, -1, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotPublishEventsVarArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.publishEvents(
                translator, -1, 2, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsVarArgWhenBatchExtendsPastEndOfArray() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, 1, 3, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsVarArgWhenBatchSizeIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, 1, -1, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotTryPublishEventsVarArgWhenBatchStartsAtIsNegative() throws Exception
    {
        RingBuffer<Object[]> ringBuffer = RingBuffer.createSingleProducer(new ArrayFactory(1), 4);
        VarArgEventTranslator translator = new VarArgEventTranslator();

        try
        {
            ringBuffer.tryPublishEvents(
                translator, -1, 2, new String[]{"Foo0", "Bar0", "Baz0", "Bam0"},
                new String[]{"Foo1", "Bar1", "Baz1", "Bam1"}, new String[]{
                    "Foo2", "Bar2",
                    "Baz2", "Bam2"
                });
        }
        finally
        {
            assertEmptyRingBuffer(ringBuffer);
        }
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
    public void shouldHandleResetToAndNotWrapUnnecessarilySingleProducer() throws Exception
    {
        assertHandleResetAndNotWrap(RingBuffer.createSingleProducer(StubEvent.EVENT_FACTORY, 4));
    }

    @Test
    public void shouldHandleResetToAndNotWrapUnnecessarilyMultiProducer() throws Exception
    {
        assertHandleResetAndNotWrap(RingBuffer.createMultiProducer(StubEvent.EVENT_FACTORY, 4));
    }

    @SuppressWarnings("deprecation")
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

    private Future<List<StubEvent>> getMessages(final long initial, final long toWaitFor) throws InterruptedException,
        BrokenBarrierException
    {
        final CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
        final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();

        final Future<List<StubEvent>> f = executor.submit(
            new TestWaiter(
                cyclicBarrier, sequenceBarrier, ringBuffer,
                initial, toWaitFor));

        cyclicBarrier.await();

        return f;
    }

    private void assertEmptyRingBuffer(final RingBuffer<Object[]> ringBuffer)
    {
        assertThat(ringBuffer.get(0)[0], is(nullValue()));
        assertThat(ringBuffer.get(1)[0], is(nullValue()));
        assertThat(ringBuffer.get(2)[0], is(nullValue()));
        assertThat(ringBuffer.get(3)[0], is(nullValue()));
    }

    private static final class TestEventProcessor implements EventProcessor
    {
        private final SequenceBarrier sequenceBarrier;
        private final Sequence sequence = new Sequence(SingleProducerSequencer.INITIAL_CURSOR_VALUE);
        private final AtomicBoolean running = new AtomicBoolean();


        TestEventProcessor(final SequenceBarrier sequenceBarrier)
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
            running.set(false);
        }

        @Override
        public boolean isRunning()
        {
            return running.get();
        }

        @Override
        public void run()
        {
            if (!running.compareAndSet(false, true))
            {
                throw new IllegalStateException("Already running");
            }
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

        ArrayFactory(int size)
        {
            this.size = size;
        }

        @Override
        public Object[] newInstance()
        {
            return new Object[size];
        }
    }

    private static class NoArgEventTranslator implements EventTranslator<Object[]>
    {
        @Override
        public void translateTo(Object[] event, long sequence)
        {
            event[0] = sequence;
        }
    }

    private static class VarArgEventTranslator implements EventTranslatorVararg<Object[]>
    {
        @Override
        public void translateTo(Object[] event, long sequence, Object... args)
        {
            event[0] = (String) args[0] + args[1] + args[2] + args[3] + "-" + sequence;
        }
    }

    private static class ThreeArgEventTranslator implements EventTranslatorThreeArg<Object[], String, String, String>
    {
        @Override
        public void translateTo(Object[] event, long sequence, String arg0, String arg1, String arg2)
        {
            event[0] = arg0 + arg1 + arg2 + "-" + sequence;
        }
    }

    private static class TwoArgEventTranslator implements EventTranslatorTwoArg<Object[], String, String>
    {
        @Override
        public void translateTo(Object[] event, long sequence, String arg0, String arg1)
        {
            event[0] = arg0 + arg1 + "-" + sequence;
        }
    }

    private static class OneArgEventTranslator implements EventTranslatorOneArg<Object[], String>
    {
        @Override
        public void translateTo(Object[] event, long sequence, String arg0)
        {
            event[0] = arg0 + "-" + sequence;
        }
    }
}
