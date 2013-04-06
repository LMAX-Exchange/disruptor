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

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class MultiProducerSequencerTest
{
    private final Mockery mockery = new Mockery();
    private AbstractSequencer publisher = new MultiProducerSequencer(1024, new BlockingWaitStrategy());
    
    @Test
    public void shouldNotBeAvailableUntilPublished() throws Exception
    {
        assertThat(publisher.isAvailable(0), is(false));
        publisher.publish(0);
        assertThat(publisher.isAvailable(0), is(true));
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
    
    @Test
    public void shouldNotifyWaitStrategyOnPublish() throws Exception
    {
        final WaitStrategy waitStrategy = mockery.mock(WaitStrategy.class);
        publisher = new MultiProducerSequencer(1024, waitStrategy);
        
        mockery.checking(new Expectations()
        {
            {
                one(waitStrategy).signalAllWhenBlocking();
            }
        });
        
        publisher.publish(0);
    }
}
