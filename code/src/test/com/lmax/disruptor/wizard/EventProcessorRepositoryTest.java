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
package com.lmax.disruptor.wizard;

import com.lmax.disruptor.DependencyBarrier;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.support.TestEvent;
import com.lmax.disruptor.wizard.stubs.NoOpEventHandler;
import org.jmock.Mockery;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;

public class EventProcessorRepositoryTest
{
    private final Mockery mockery = new Mockery();

    private EventProcessorRepository<TestEvent> eventprocessorRepository;
    private EventProcessor eventprocessor1;
    private EventProcessor eventprocessor2;
    private NoOpEventHandler handler1;
    private NoOpEventHandler handler2;
    private DependencyBarrier barrier1;
    private DependencyBarrier barrier2;

    @SuppressWarnings({"unchecked"})
    @Before
    public void setUp() throws Exception
    {
        eventprocessorRepository = new EventProcessorRepository<TestEvent>();
        eventprocessor1 = mockery.mock(EventProcessor.class, "eventProcessor1");
        eventprocessor2 = mockery.mock(EventProcessor.class, "eventProcessor2");
        handler1 = new NoOpEventHandler();
        handler2 = new NoOpEventHandler();

        barrier1 = mockery.mock(DependencyBarrier.class, "barrier1");
        barrier2 = mockery.mock(DependencyBarrier.class, "barrier2");
    }

    @Test
    public void shouldGetBarrierByHandler() throws Exception
    {
        eventprocessorRepository.add(eventprocessor1, handler1, barrier1);

        assertThat(eventprocessorRepository.getBarrierFor(handler1), sameInstance(barrier1));
    }

    @Test
    public void shouldReturnNullForBarrierWhenHandlerIsNotRegistered() throws Exception
    {
        assertThat(eventprocessorRepository.getBarrierFor(handler1), is(nullValue()));
    }

    @Test
    public void shouldGetLastEventProcessorsInChain() throws Exception
    {
        eventprocessorRepository.add(eventprocessor1, handler1, barrier1);
        eventprocessorRepository.add(eventprocessor2, handler2, barrier2);

        eventprocessorRepository.unmarkEventProcessorsAsEndOfChain(eventprocessor2);

        final EventProcessor[] lastEventProcessorsInChain = eventprocessorRepository.getLastEventProcessorsInChain();
        assertThat(lastEventProcessorsInChain.length, equalTo(1));
        assertThat(lastEventProcessorsInChain[0], sameInstance(eventprocessor1));
    }

    @Test
    public void shouldRetrieveEventProcessorForHandler() throws Exception
    {
        eventprocessorRepository.add(eventprocessor1, handler1, barrier1);

        assertThat(eventprocessorRepository.getEventProcessorFor(handler1), sameInstance(eventprocessor1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldThowExceptionWhenHandlerIsNotRegistered() throws Exception
    {
        eventprocessorRepository.getEventProcessorFor(new NoOpEventHandler());
    }

    @Test
    public void shouldIterateAllEventProcessors() throws Exception
    {
        eventprocessorRepository.add(eventprocessor1, handler1, barrier1);
        eventprocessorRepository.add(eventprocessor2, handler2, barrier2);


        boolean seen1 = false;
        boolean seen2 = false;
        for (EventProcessorInfo<TestEvent> testEntryEventProcessorInfo : eventprocessorRepository)
        {
            if (!seen1 && testEntryEventProcessorInfo.getEventProcessor() == eventprocessor1 && testEntryEventProcessorInfo.getHandler() == handler1)
            {
                seen1 = true;
            }
            else if (!seen2 && testEntryEventProcessorInfo.getEventProcessor() == eventprocessor2 && testEntryEventProcessorInfo.getHandler() == handler2)
            {
                seen2 = true;
            }
            else
            {
                fail("Unexpected eventprocessor info: " + testEntryEventProcessorInfo);
            }
        }
        assertTrue("Included eventprocessor 1", seen1);
        assertTrue("Included eventprocessor 2", seen2);
    }
}
