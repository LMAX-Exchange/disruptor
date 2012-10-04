package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class MultiProducerPublisherTest
{
    private final Mockery mockery = new Mockery();
    private MultiProducerPublisher publisher = new MultiProducerPublisher(1024, new BlockingWaitStrategy());
    
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
        publisher = new MultiProducerPublisher(1024, waitStrategy);
        
        mockery.checking(new Expectations()
        {
            {
                one(waitStrategy).signalAllWhenBlocking();
            }
        });
        
        publisher.publish(0);
    }
}
