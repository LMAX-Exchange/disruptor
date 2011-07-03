/*
 * Copyright 2011 LMAX Ltd.
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
