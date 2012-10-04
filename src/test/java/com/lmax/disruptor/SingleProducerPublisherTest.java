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
