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

import com.lmax.disruptor.dsl.SequencerFactory;
import com.lmax.disruptor.support.LongEvent;
import com.lmax.disruptor.support.SequencerFactories;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class EventPublisherTest implements EventTranslator<LongEvent>
{
    private static final int BUFFER_SIZE = 32;
    private RingBuffer<LongEvent> ringBuffer;

    public EventPublisherTest(String name, SequencerFactory factory)
    {
        ringBuffer = new RingBuffer<>(LongEvent.FACTORY, factory.newInstance(BUFFER_SIZE, new BlockingWaitStrategy()));
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters()
    {
        return SequencerFactories.asParameters();
    }

    @Test
    public void shouldPublishEvent()
    {
        ringBuffer.addGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());

        ringBuffer.publishEvent(this);
        ringBuffer.publishEvent(this);

        assertThat(ringBuffer.get(0).get(), is(29L));
        assertThat(ringBuffer.get(1).get(), is(1 + 29L));
    }

    @Test
    public void shouldTryPublishEvent() throws Exception
    {
        ringBuffer.addGatingSequences(new Sequence());

        int count = 0;
        while (ringBuffer.hasAvailableCapacity(1))
        {
            assertThat(ringBuffer.tryPublishEvent(this), is(true));
            count++;
        }

        assertThat(count, is(greaterThan(0)));

        for (int i = 0; i < count; i++)
        {
            assertThat(ringBuffer.get(i).get(), is(i + 29L));
        }

        assertThat(ringBuffer.tryPublishEvent(this), is(false));
    }

    @Override
    public void translateTo(LongEvent event, long sequence)
    {
        event.set(sequence + 29);
    }
}
