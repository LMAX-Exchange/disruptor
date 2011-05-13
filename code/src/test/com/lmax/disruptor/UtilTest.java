package com.lmax.disruptor;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public final class UtilTest
{
    private final Mockery context = new Mockery();

    @Test
    public void shouldReturnNextPowerOfTwo()
    {
        int powerOfTwo = Util.ceilingNextPowerOfTwo(1000);

        Assert.assertEquals(1024, powerOfTwo);
    }

    @Test
    public void shouldReturnExactPowerOfTwo()
    {
        int powerOfTwo = Util.ceilingNextPowerOfTwo(1024);

        Assert.assertEquals(1024, powerOfTwo);
    }

    @Test
    public void shouldReturnMinimumSequence()
    {
        final Consumer[] consumers = new Consumer[3];
        consumers[0] = context.mock(Consumer.class, "c0");
        consumers[1] = context.mock(Consumer.class, "c1");
        consumers[2] = context.mock(Consumer.class, "c2");

        context.checking(new Expectations()
        {
            {
                oneOf(consumers[0]).getSequence();
                will(returnValue(Long.valueOf(7L)));

                oneOf(consumers[1]).getSequence();
                will(returnValue(Long.valueOf(3L)));

                oneOf(consumers[2]).getSequence();
                will(returnValue(Long.valueOf(12L)));
            }
        });

        Assert.assertEquals(3L, Util.getMinimumSequence(consumers));
    }

    @Test
    public void shouldReturnLongMaxWhenNoConsumers()
    {
        final Consumer[] consumers = new Consumer[0];

        Assert.assertEquals(Long.MAX_VALUE, Util.getMinimumSequence(consumers));
    }
}
