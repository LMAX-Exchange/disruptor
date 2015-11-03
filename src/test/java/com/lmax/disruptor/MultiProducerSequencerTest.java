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

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.dsl.SequencerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;

import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class MultiProducerSequencerTest
{
    private final Sequencer publisher;

    @SuppressWarnings("unused")
    public MultiProducerSequencerTest(String name, SequencerFactory sequencerFactory)
    {
        publisher = sequencerFactory.newInstance(1024, new BlockingWaitStrategy());
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> parameters()
    {
        return asList(
            new Object[][] {
                {"waitfree", ProducerType.waitFree(64)},
                {"multi", ProducerType.MULTI}
            });
    }

    @Test
    public void shouldOnlyAllowMessagesToBeAvailableIfSpecificallyPublished() throws Exception
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
}
