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
public class SingleProducerPublisherTest
{
    private final Mockery mockery = new Mockery();
    private SingleProducerPublisher publisher = new SingleProducerPublisher(new BlockingWaitStrategy());

    @Test
    public void shouldNotBeAvailableUntilPublished() throws Exception
    {
        assertThat(publisher.isAvailable(0), is(false));
        publisher.publish(5);
        
        for (int i = 0; i <= 5; i++)
        {
            assertThat(publisher.isAvailable(i), is(true));
        }
        
        assertThat(publisher.isAvailable(6), is(false));
    }

    
    @Test
    public void shouldNotifyWaitStrategyOnPublish() throws Exception
    {
        final WaitStrategy waitStrategy = mockery.mock(WaitStrategy.class);
        publisher = new SingleProducerPublisher(waitStrategy);
        
        mockery.checking(new Expectations()
        {
            {
                one(waitStrategy).signalAllWhenBlocking();
            }
        });
        
        publisher.publish(0);
    }
}
