/*
 * Copyright 2012 LMAX Ltd.
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

import com.lmax.disruptor.util.Util;

import java.util.Arrays;

/**
 * Hides a group of Sequences behind a single Sequence
 */
public final class FixedSequenceGroup extends Sequence
{
    private final Sequence[] sequences;

    /**
     * Constructor
     *
     * @param sequences the list of sequences to be tracked under this sequence group
     */
    public FixedSequenceGroup(Sequence[] sequences)
    {
        this.sequences = Arrays.copyOf(sequences, sequences.length);
    }

    /**
     * Get the minimum sequence value for the group.
     *
     * @return the minimum sequence value for the group.
     */
    @Override
    public long get()
    {
        return Util.getMinimumSequence(sequences);
    }

    @Override
    public String toString()
    {
        return Arrays.toString(sequences);
    }

    /**
     * Not supported.
     */
    @Override
    public void set(long value)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public boolean compareAndSet(long expectedValue, long newValue)
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public long incrementAndGet()
    {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     */
    @Override
    public long addAndGet(long increment)
    {
        throw new UnsupportedOperationException();
    }
}
