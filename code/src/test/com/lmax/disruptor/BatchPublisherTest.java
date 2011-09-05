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

import com.lmax.disruptor.support.StubEvent;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public final class BatchPublisherTest
{
    private final RingBuffer<StubEvent> ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 32);
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    {
        ringBuffer.setGatingSequences(new NoOpEventProcessor(ringBuffer).getSequence());
    }

    @Test
    public void shouldClaimBatchAndPublishBack() throws Exception
    {
        final int batchSize = 5;
        final SequenceBatch sequenceBatch = new SequenceBatch(batchSize);

        ringBuffer.next(sequenceBatch);

        assertThat(Long.valueOf(sequenceBatch.getStart()), is(Long.valueOf(0L)));
        assertThat(Long.valueOf(sequenceBatch.getEnd()), is(Long.valueOf(4L)));
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(Sequencer.INITIAL_CURSOR_VALUE)));

        ringBuffer.publish(sequenceBatch);

        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(batchSize - 1L)));
        assertThat(Long.valueOf(sequenceBarrier.waitFor(0L)), is(Long.valueOf(batchSize - 1L)));
    }
}
