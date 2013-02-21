package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class TimeoutBlockingWaitStrategyTest
{
    private final Mockery mockery = new Mockery();
    
    @Test
    public void shouldTimeoutWaitFor() throws Exception
    {
        final SequenceBarrier sequenceBarrier = mockery.mock(SequenceBarrier.class);
        
        long theTimeout = 500;
        TimeoutBlockingWaitStrategy waitStrategy = new TimeoutBlockingWaitStrategy(theTimeout, TimeUnit.MILLISECONDS);
        Sequence cursor = new Sequence(5);
        Sequence dependent = cursor;
        
        mockery.checking(new Expectations()
        {
            {
                allowing(sequenceBarrier).checkAlert();
            }
        });
        
        long t0 = System.currentTimeMillis();
        
        long sequence = waitStrategy.waitFor(6, cursor, dependent, sequenceBarrier);
        
        long t1 = System.currentTimeMillis();
        
        long timeWaiting = t1 - t0;
        
        assertThat(sequence, is(5L));
        assertThat(timeWaiting, greaterThanOrEqualTo(theTimeout));
    }
}
