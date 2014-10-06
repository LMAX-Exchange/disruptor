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
package com.lmax.disruptor.dsl;

import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.dsl.stubs.SleepingEventHandler;
import com.lmax.disruptor.support.TestEvent;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class ConsumerRepositoryTest
{
    private final Mockery mockery = new Mockery();

    private ConsumerRepository<TestEvent> consumerRepository;
    private EventProcessor eventProcessor1;
    private EventProcessor eventProcessor2;
    private SleepingEventHandler handler1;
    private SleepingEventHandler handler2;
    private SequenceBarrier barrier1;
    private SequenceBarrier barrier2;

    @Before
    public void setUp() throws Exception
    {
        consumerRepository = new ConsumerRepository<TestEvent>();
        eventProcessor1 = mockery.mock(EventProcessor.class, "eventProcessor1");
        eventProcessor2 = mockery.mock(EventProcessor.class, "eventProcessor2");

        final Sequence sequence1 = new Sequence();
        final Sequence sequence2 = new Sequence();
        mockery.checking(new Expectations()
        {
            {
                allowing(eventProcessor1).getSequence();
                will(returnValue(sequence1));

                allowing(eventProcessor1).isRunning();
                will(returnValue(true));

                allowing(eventProcessor2).getSequence();
                will(returnValue(sequence2));

                allowing(eventProcessor2).isRunning();
                will(returnValue(true));
            }
        });
        handler1 = new SleepingEventHandler();
        handler2 = new SleepingEventHandler();

        barrier1 = mockery.mock(SequenceBarrier.class, "barrier1");
        barrier2 = mockery.mock(SequenceBarrier.class, "barrier2");
    }

    @Test
    public void shouldGetBarrierByHandler() throws Exception
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(consumerRepository.getBarrierFor(handler1), sameInstance(barrier1));
    }

    @Test
    public void shouldReturnNullForBarrierWhenHandlerIsNotRegistered() throws Exception
    {
        assertThat(consumerRepository.getBarrierFor(handler1), is(nullValue()));
    }

    @Test
    public void shouldGetLastEventProcessorsInChain() throws Exception
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);
        consumerRepository.add(eventProcessor2, handler2, barrier2);

        consumerRepository.unMarkEventProcessorsAsEndOfChain(eventProcessor2.getSequence());


        final Sequence[] lastEventProcessorsInChain = consumerRepository.getLastSequenceInChain(true);
        assertThat(lastEventProcessorsInChain.length, equalTo(1));
        assertThat(lastEventProcessorsInChain[0], sameInstance(eventProcessor1.getSequence()));
    }

    @Test
    public void shouldRetrieveEventProcessorForHandler() throws Exception
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(consumerRepository.getEventProcessorFor(handler1), sameInstance(eventProcessor1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenHandlerIsNotRegistered() throws Exception
    {
        consumerRepository.getEventProcessorFor(new SleepingEventHandler());
    }

    @Test
    public void shouldIterateAllEventProcessors() throws Exception
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);
        consumerRepository.add(eventProcessor2, handler2, barrier2);

        boolean seen1 = false;
        boolean seen2 = false;
        for (ConsumerInfo testEntryEventProcessorInfo : consumerRepository)
        {
            final EventProcessorInfo<?> eventProcessorInfo = (EventProcessorInfo<?>) testEntryEventProcessorInfo;
            if (!seen1 && eventProcessorInfo.getEventProcessor() == eventProcessor1 &&
                eventProcessorInfo.getHandler() == handler1)
            {
                seen1 = true;
            }
            else if (!seen2 && eventProcessorInfo.getEventProcessor() == eventProcessor2 &&
                     eventProcessorInfo.getHandler() == handler2)
            {
                seen2 = true;
            }
            else
            {
                fail("Unexpected eventProcessor info: " + testEntryEventProcessorInfo);
            }
        }

        assertTrue("Included eventProcessor 1", seen1);
        assertTrue("Included eventProcessor 2", seen2);
    }
}
