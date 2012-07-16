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
    private final PreallocatedRingBuffer<StubEvent> ringBuffer = new PreallocatedRingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, 32);
    private final Sequencer sequencer = ringBuffer.getSequencer();
    private final SequenceBarrier sequenceBarrier = ringBuffer.newBarrier();
    {
        ringBuffer.setGatingSequences(new NoOpEventProcessor(sequencer).getSequence());
    }

    @Test
    public void shouldClaimBatchAndPublishBack() throws Exception
    {
        final int batchSize = 5;
        final BatchDescriptor batchDescriptor = ringBuffer.newBatchDescriptor(batchSize);

        sequencer.next(batchDescriptor);

        assertThat(Long.valueOf(batchDescriptor.getStart()), is(Long.valueOf(0L)));
        assertThat(Long.valueOf(batchDescriptor.getEnd()), is(Long.valueOf(4L)));
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(SingleProducerSequencer.INITIAL_CURSOR_VALUE)));

        sequencer.publish(batchDescriptor);

        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(batchSize - 1L)));
        assertThat(Long.valueOf(sequenceBarrier.waitFor(0L)), is(Long.valueOf(batchSize - 1L)));
    }
}
