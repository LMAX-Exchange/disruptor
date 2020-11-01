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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultiProducerSequencerTest
{
    private final Sequencer publisher = new MultiProducerSequencer(1024, new BlockingWaitStrategy());

    @Test
    public void shouldOnlyAllowMessagesToBeAvailableIfSpecificallyPublished() throws Exception
    {
        publisher.publish(3);
        publisher.publish(5);

        assertFalse(publisher.isAvailable(0));
        assertFalse(publisher.isAvailable(1));
        assertFalse(publisher.isAvailable(2));
        assertTrue(publisher.isAvailable(3));
        assertFalse(publisher.isAvailable(4));
        assertTrue(publisher.isAvailable(5));
        assertFalse(publisher.isAvailable(6));
    }
}
