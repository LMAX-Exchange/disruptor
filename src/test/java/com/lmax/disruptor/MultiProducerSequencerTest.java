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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class MultiProducerSequencerTest
{
    

    
    /* CONSTANTS*/
    private static final int BUFFER_SIZE = 1024;
    private static final int AVAILABLE_SEQUENCE_3 = 3;
    private static final int AVAILABLE_SEQUENCE_5 = 5;

    private final Sequencer publisher = new MultiProducerSequencer(BUFFER_SIZE, new BlockingWaitStrategy());

    @Test
    public void shouldOnlyAllowMessagesToBeAvailableIfSpecificallyPublished() throws Exception
    {
        publisher.publish(AVAILABLE_SEQUENCE_3);
        publisher.publish(AVAILABLE_SEQUENCE_5);

        assertThat(publisher.isAvailable(0), is(false));
        assertThat(publisher.isAvailable(1), is(false));
        assertThat(publisher.isAvailable(2), is(false));
        assertThat(publisher.isAvailable(AVAILABLE_SEQUENCE_3), is(true));
        assertThat(publisher.isAvailable(4), is(false));
        assertThat(publisher.isAvailable(AVAILABLE_SEQUENCE_5), is(true));
        assertThat(publisher.isAvailable(6), is(false));
    }
}
