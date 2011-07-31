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

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public final class BatchProducerTest
{
    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 20);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    {
        ringBuffer.setTrackedConsumers(new NoOpConsumer(ringBuffer));
    }

    @Test
    public void shouldClaimBatchAndCommitBack() throws Exception
    {
        final int batchSize = 5;
        final SequenceBatch sequenceBatch = new SequenceBatch(batchSize);

        ringBuffer.nextEntries(sequenceBatch);

        assertThat(Long.valueOf(sequenceBatch.getStart()), is(Long.valueOf(0L)));
        assertThat(Long.valueOf(sequenceBatch.getEnd()), is(Long.valueOf(4L)));
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(RingBuffer.INITIAL_CURSOR_VALUE)));

        ringBuffer.commit(sequenceBatch);

        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(batchSize - 1L)));
        assertThat(Long.valueOf(consumerBarrier.waitFor(0L)), is(Long.valueOf(batchSize - 1L)));
    }
}
