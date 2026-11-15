/*
 * Copyright 2012 LMAX Ltd.
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

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class MultiProducerSequencerTest
{
    private final Sequencer publisher = new MultiProducerSequencer(1024, new BlockingWaitStrategy());

    @Test
    public void shouldOnlyAllowMessagesToBeAvailableIfSpecificallyPublished()
    {
        publisher.publish(3);
        publisher.publish(5);

        assertThat(publisher.isAvailable(0), is(false));
        assertThat(publisher.isAvailable(1), is(false));
        assertThat(publisher.isAvailable(2), is(false));
        assertThat(publisher.isAvailable(3), is(true));
        assertThat(publisher.isAvailable(4), is(false));
        assertThat(publisher.isAvailable(5), is(true));
        assertThat(publisher.isAvailable(6), is(false));
    }

    @Test
    public void shouldTrackEachSequenceInItsOwnSlotForEveryBufferSize()
    {
        for (int bufferSize = 1; bufferSize <= 1024; bufferSize <<= 1)
        {
            final Sequencer sequencer = new MultiProducerSequencer(bufferSize, new BlockingWaitStrategy());

            for (long sequence = 0; sequence < bufferSize; sequence++)
            {
                sequencer.publish(sequence);

                for (long other = 0; other < bufferSize; other++)
                {
                    assertThat(
                        "bufferSize " + bufferSize + ", published up to " + sequence + ", checking " + other,
                        sequencer.isAvailable(other), is(other <= sequence));
                }
                assertThat(sequencer.getHighestPublishedSequence(0, bufferSize - 1), is(sequence));
            }
        }
    }

    @Test
    public void shouldReportHighestPublishedSequenceUpToFirstGapAfterWrapping()
    {
        final int bufferSize = 64;
        final Sequencer sequencer = new MultiProducerSequencer(bufferSize, new BlockingWaitStrategy());

        for (long sequence = 0; sequence < 3L * bufferSize; sequence++)
        {
            if (sequence != 150)
            {
                sequencer.publish(sequence);
            }
        }

        assertThat(sequencer.getHighestPublishedSequence(128, 191), is(149L));
        assertThat(sequencer.isAvailable(150), is(false));
        assertThat(sequencer.isAvailable(151), is(true));
    }
}
