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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Version of AtomicLong with cache line padding to prevent false sharing.
 */
public class PaddedAtomicLong extends AtomicLong
{
    public volatile long p1, p2, p3, p4, p5, p6 = 7L;

    /**
     * Default constructor
     */
    public PaddedAtomicLong()
    {
    }

    /**
     * Construct with an initial value.
     *
     * @param initialValue for initialisation
     */
    public PaddedAtomicLong(final long initialValue)
    {
        super(initialValue);
    }

    public long sumPaddingToPreventOptimisation()
    {
        return p1 + p2 + p3 + p4 + p5 + p6;
    }
}
