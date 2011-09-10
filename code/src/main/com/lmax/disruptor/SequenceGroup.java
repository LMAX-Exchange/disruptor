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

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Group {@link Sequence} that can dynamically have {@link Sequence}s added and removed while being
 * thread safe.
 * <p>
 * The {@link SequenceGroup#get()} and {@link SequenceGroup#set(long)} methods are lock free and can be
 * concurrently be called with the {@link SequenceGroup#add(Sequence)} and {@link SequenceGroup#remove(Sequence)}.
 */
public final class SequenceGroup extends Sequence
{
    private final CopyOnWriteArrayList<Sequence> aggregateSequences = new CopyOnWriteArrayList<Sequence>();

    /**
     * Default Constructor
     */
    public SequenceGroup()
    {
    }

    /**
     * Get the minimum sequence value for the group.
     *
     * @return the minimum sequence value for the group.
     */
    @Override
    public long get()
    {
        long minimum = Long.MAX_VALUE;

        for (final Sequence sequence : aggregateSequences)
        {
            long sequenceMin = sequence.get();
            minimum = minimum < sequenceMin ? minimum : sequenceMin;
        }

        return minimum;
    }

    /**
     * Set all {@link Sequence}s in the group to a given value.
     *
     * @param value to set the group of sequences to.
     */
    @Override
    public void set(final long value)
    {
        for (final Sequence sequence : aggregateSequences)
        {
            sequence.set(value);
        }
    }

    /**
     * Add a {@link Sequence} into this aggregate.
     *
     * @param sequence to be added to the aggregate.
     */
    public void add(final Sequence sequence)
    {
        aggregateSequences.add(sequence);
    }

    /**
     * Remove the first occurrence of the {@link Sequence} from this aggregate.
     *
     * @param sequence to be removed from this aggregate.
     * @return true if the sequence was removed otherwise false.
     */
    public boolean remove(final Sequence sequence)
    {
        return aggregateSequences.remove(sequence);
    }

    /**
     * Get the size of the group.
     *
     * @return the size of the group.
     */
    public int size()
    {
        return aggregateSequences.size();
    }
}
