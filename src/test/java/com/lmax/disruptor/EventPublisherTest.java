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
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.lmax.disruptor.support.LongEvent;

public class EventPublisherTest implements EventTranslator<LongEvent>
{
    private static final int BUFFER_SIZE = 32;
    private RingBuffer<LongEvent> ringBuffer = createMultiProducer(LongEvent.FACTORY, BUFFER_SIZE);

    @Test
    public void shouldPublishEvent()
    {
        ringBuffer.addGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());

        ringBuffer.publishEvent(this);
        ringBuffer.publishEvent(this);

        assertThat(Long.valueOf(ringBuffer.get(0).get()), is(Long.valueOf(0 + 29L)));
        assertThat(Long.valueOf(ringBuffer.get(1).get()), is(Long.valueOf(1 + 29L)));
    }

    @Test
    public void shouldTryPublishEvent() throws Exception
    {
        ringBuffer.addGatingSequences(new Sequence());

        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            assertThat(ringBuffer.tryPublishEvent(this), is(true));
        }

        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            assertThat(Long.valueOf(ringBuffer.get(i).get()), is(Long.valueOf(i + 29L)));
        }

        assertThat(ringBuffer.tryPublishEvent(this), is(false));
    }

    @Override
    public void translateTo(LongEvent event, long sequence)
    {
        event.set(sequence + 29);
    }
}
