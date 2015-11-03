package com.lmax.disruptor;

import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public class TimeoutBlockingWaitStrategyTest
{
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    @Test
    public void shouldTimeoutWaitFor() throws Exception
    {
        final SequenceBarrier sequenceBarrier = mockery.mock(SequenceBarrier.class);

        long theTimeout = 500;
        TimeoutBlockingWaitStrategy waitStrategy = new TimeoutBlockingWaitStrategy(theTimeout, TimeUnit.MILLISECONDS);
        Sequence cursor = new Sequence(5);
        Sequence dependent = cursor;

        mockery.checking(
            new Expectations()
            {
                {
                    allowing(sequenceBarrier).checkAlert();
                }
            });

        long t0 = System.currentTimeMillis();

        try
        {
            waitStrategy.waitFor(6, cursor, dependent, sequenceBarrier);
            fail("TimeoutException should have been thrown");
        }
        catch (TimeoutException e)
        {
        }

        long t1 = System.currentTimeMillis();

        long timeWaiting = t1 - t0;

        assertThat(timeWaiting, greaterThanOrEqualTo(theTimeout));
    }
}
