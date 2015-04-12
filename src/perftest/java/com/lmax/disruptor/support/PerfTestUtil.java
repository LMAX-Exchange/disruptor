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
package com.lmax.disruptor.support;

public final class PerfTestUtil
{
    public static long accumulatedAddition(final long iterations)
    {
        long temp = 0L;
        for (long i = 0L; i < iterations; i++)
        {
            temp += i;
        }

        return temp;
    }

    public static void failIf(long a, long b)
    {
        if (a == b)
        {
            throw new RuntimeException();
        }
    }

    public static void failIfNot(long a, long b)
    {
        if (a != b)
        {
            throw new RuntimeException();
        }
    }
}
