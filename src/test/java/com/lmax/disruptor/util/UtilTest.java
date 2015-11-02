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
package com.lmax.disruptor.util;

import com.lmax.disruptor.Sequence;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

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
        final Sequence[] sequences = new Sequence[3];

        context.setImposteriser(ClassImposteriser.INSTANCE);

        sequences[0] = new Sequence(7);
        sequences[1] = new Sequence(3);
        sequences[2] = new Sequence(12);

        Assert.assertEquals(3L, Util.getMinimumSequence(sequences));
    }

    @Test
    public void shouldReturnLongMaxWhenNoEventProcessors()
    {
        final Sequence[] sequences = new Sequence[0];

        Assert.assertEquals(Long.MAX_VALUE, Util.getMinimumSequence(sequences));
    }

    @Test
    public void shouldGetByteBufferAddress() throws Exception
    {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        assertThat(Util.getAddressFromDirectByteBuffer(buffer), is(not(0L)));
    }
}
