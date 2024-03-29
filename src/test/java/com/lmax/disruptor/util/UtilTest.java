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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public final class UtilTest
{
    @Test
    public void shouldReturnNextPowerOfTwo()
    {
        int powerOfTwo = Util.ceilingNextPowerOfTwo(1000);

        assertEquals(1024, powerOfTwo);
    }

    @Test
    public void shouldReturnExactPowerOfTwo()
    {
        int powerOfTwo = Util.ceilingNextPowerOfTwo(1024);

        assertEquals(1024, powerOfTwo);
    }

    @Test
    public void shouldReturnMinimumSequence()
    {
        final Sequence[] sequences = {new Sequence(7L), new Sequence(3L), new Sequence(12L)};
        assertEquals(3L, Util.getMinimumSequence(sequences));
    }

    @Test
    public void shouldReturnLongMaxWhenNoEventProcessors()
    {
        final Sequence[] sequences = new Sequence[0];

        assertEquals(Long.MAX_VALUE, Util.getMinimumSequence(sequences));
    }

    @Test
    void shouldThrowErrorIfValuePassedToLog2FunctionIsNotPositive()
    {
        assertThrows(IllegalArgumentException.class, () -> Util.log2(0));
        assertThrows(IllegalArgumentException.class, () -> Util.log2(-1));
        assertThrows(IllegalArgumentException.class, () -> Util.log2(Integer.MIN_VALUE));
    }

    @Test
    void shouldCalculateCorrectlyIntegerFlooredLog2()
    {
        assertEquals(0, Util.log2(1));
        assertEquals(1, Util.log2(2));
        assertEquals(1, Util.log2(3));
        assertEquals(10, Util.log2(1024));
        assertEquals(30, Util.log2(Integer.MAX_VALUE));
    }
}
