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

import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.dsl.stubs.SleepingEventHandler;
import com.lmax.disruptor.support.TestEvent;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class EventProcessorRepositoryTest
{
    private final Mockery mockery = new Mockery();

    private EventProcessorRepository<TestEvent> eventProcessorRepository;
    private EventProcessor eventProcessor1;
    private EventProcessor eventProcessor2;
    private SleepingEventHandler handler1;
    private SleepingEventHandler handler2;
    private SequenceBarrier barrier1;
    private SequenceBarrier barrier2;

    @SuppressWarnings({"unchecked"})
    @Before
    public void setUp() throws Exception
    {
        eventProcessorRepository = new EventProcessorRepository<TestEvent>();
        eventProcessor1 = mockery.mock(EventProcessor.class, "eventProcessor1");
        eventProcessor2 = mockery.mock(EventProcessor.class, "eventProcessor2");
        handler1 = new SleepingEventHandler();
        handler2 = new SleepingEventHandler();

        barrier1 = mockery.mock(SequenceBarrier.class, "barrier1");
        barrier2 = mockery.mock(SequenceBarrier.class, "barrier2");
    }

    @Test
    public void shouldGetBarrierByHandler() throws Exception
    {
        eventProcessorRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(eventProcessorRepository.getBarrierFor(handler1), sameInstance(barrier1));
    }

    @Test
    public void shouldReturnNullForBarrierWhenHandlerIsNotRegistered() throws Exception
    {
        assertThat(eventProcessorRepository.getBarrierFor(handler1), is(nullValue()));
    }

    @Test
    public void shouldGetLastEventProcessorsInChain() throws Exception
    {
        eventProcessorRepository.add(eventProcessor1, handler1, barrier1);
        eventProcessorRepository.add(eventProcessor2, handler2, barrier2);

        eventProcessorRepository.unMarkEventProcessorsAsEndOfChain(eventProcessor2);

        final EventProcessor[] lastEventProcessorsInChain = eventProcessorRepository.getLastEventProcessorsInChain();
        assertThat(Integer.valueOf(lastEventProcessorsInChain.length), equalTo(Integer.valueOf(1)));
        assertThat(lastEventProcessorsInChain[0], sameInstance(eventProcessor1));
    }

    @Test
    public void shouldRetrieveEventProcessorForHandler() throws Exception
    {
        eventProcessorRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(eventProcessorRepository.getEventProcessorFor(handler1), sameInstance(eventProcessor1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenHandlerIsNotRegistered() throws Exception
    {
        eventProcessorRepository.getEventProcessorFor(new SleepingEventHandler());
    }

    @Test
    public void shouldIterateAllEventProcessors() throws Exception
    {
        eventProcessorRepository.add(eventProcessor1, handler1, barrier1);
        eventProcessorRepository.add(eventProcessor2, handler2, barrier2);

        boolean seen1 = false;
        boolean seen2 = false;
        for (EventProcessorInfo<TestEvent> testEntryEventProcessorInfo : eventProcessorRepository)
        {
            if (!seen1 && testEntryEventProcessorInfo.getEventProcessor() == eventProcessor1 &&
                testEntryEventProcessorInfo.getHandler() == handler1)
            {
                seen1 = true;
            }
            else if (!seen2 && testEntryEventProcessorInfo.getEventProcessor() == eventProcessor2 &&
                     testEntryEventProcessorInfo.getHandler() == handler2)
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
