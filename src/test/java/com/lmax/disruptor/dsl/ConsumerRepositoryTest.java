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
import com.lmax.disruptor.support.DummyEventProcessor;
import com.lmax.disruptor.support.DummySequenceBarrier;
import com.lmax.disruptor.support.TestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class ConsumerRepositoryTest
{
    private ConsumerRepository<TestEvent> consumerRepository;
    private EventProcessor eventProcessor1;
    private EventProcessor eventProcessor2;
    private SleepingEventHandler handler1;
    private SleepingEventHandler handler2;
    private SequenceBarrier barrier1;
    private SequenceBarrier barrier2;

    @BeforeEach
    public void setUp()
    {
        consumerRepository = new ConsumerRepository<>();
        eventProcessor1 = new DummyEventProcessor(new Sequence());
        eventProcessor2 = new DummyEventProcessor(new Sequence());

        eventProcessor1.run();
        eventProcessor2.run();

        handler1 = new SleepingEventHandler();
        handler2 = new SleepingEventHandler();

        barrier1 = new DummySequenceBarrier();
        barrier2 = new DummySequenceBarrier();
    }

    @Test
    public void shouldGetBarrierByHandler()
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(consumerRepository.getBarrierFor(handler1), sameInstance(barrier1));
    }

    @Test
    public void shouldReturnNullForBarrierWhenHandlerIsNotRegistered()
    {
        assertThat(consumerRepository.getBarrierFor(handler1), is(nullValue()));
    }

    @Test
    @Deprecated
    public void shouldGetLastEventProcessorsInChain()
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);
        consumerRepository.add(eventProcessor2, handler2, barrier2);

        consumerRepository.unMarkEventProcessorsAsEndOfChain(eventProcessor2.getSequence());


        final Sequence[] lastEventProcessorsInChain = consumerRepository.getLastSequenceInChain(true);
        assertThat(lastEventProcessorsInChain.length, equalTo(1));
        assertThat(lastEventProcessorsInChain[0], sameInstance(eventProcessor1.getSequence()));
    }

    @Test
    public void shouldRetrieveEventProcessorForHandler()
    {
        consumerRepository.add(eventProcessor1, handler1, barrier1);

        assertThat(consumerRepository.getEventProcessorFor(handler1), sameInstance(eventProcessor1));
    }

    @Test
    public void shouldThrowExceptionWhenHandlerIsNotRegistered()
    {
        assertThrows(IllegalArgumentException.class, () ->
                consumerRepository.getEventProcessorFor(new SleepingEventHandler())
        );
    }

    @Test
    public void shouldIterateAllEventProcessors()
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

        assertTrue(seen1, "Included eventProcessor 1");
        assertTrue(seen2, "Included eventProcessor 2");
    }
}
