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

import com.lmax.disruptor.util.Util;

import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link Sequence} group that can dynamically have {@link Sequence}s added and removed while being
 * thread safe.
 * <p>
 * The {@link SequenceGroup#get()} and {@link SequenceGroup#set(long)} methods are lock free and can be
 * concurrently be called with the {@link SequenceGroup#add(Sequence)} and {@link SequenceGroup#remove(Sequence)}.
 */
public final class SequenceGroup extends Sequence
{
    private final AtomicReference<Sequence[]> sequencesRef = new AtomicReference<Sequence[]>(new Sequence[0]);

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
        return Util.getMinimumSequence(sequencesRef.get());
    }

    /**
     * Set all {@link Sequence}s in the group to a given value.
     *
     * @param value to set the group of sequences to.
     */
    @Override
    public void set(final long value)
    {
        final Sequence[] sequences = sequencesRef.get();
        for (int i = 0, size = sequences.length; i < size; i++)
        {
            sequences[i].set(value);
        }
    }

    /**
     * Add a {@link Sequence} into this aggregate.
     *
     * @param sequence to be added to the aggregate.
     */
    public void add(final Sequence sequence)
    {
        Sequence[] oldSequences;
        Sequence[] newSequences;
        do
        {
            oldSequences = sequencesRef.get();
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize + 1];
            System.arraycopy(oldSequences, 0, newSequences, 0, oldSize);
            newSequences[oldSize] = sequence;
        }
        while (!sequencesRef.compareAndSet(oldSequences, newSequences));
    }

    /**
     * Remove the first occurrence of the {@link Sequence} from this aggregate.
     *
     * @param sequence to be removed from this aggregate.
     * @return true if the sequence was removed otherwise false.
     */
    public boolean remove(final Sequence sequence)
    {
        boolean found = false;
        Sequence[] oldSequences;
        Sequence[] newSequences;
        do
        {
            oldSequences = sequencesRef.get();
            final int oldSize = oldSequences.length;
            newSequences = new Sequence[oldSize - 1];

            int pos = 0;
            for (int i = 0; i < oldSize; i++)
            {
                final Sequence testSequence = oldSequences[i];
                if (sequence == testSequence && !found)
                {
                    found = true;
                }
                else
                {
                    newSequences[pos++] = testSequence;
                }
            }

            if (!found)
            {
                break;
            }
        }
        while (!sequencesRef.compareAndSet(oldSequences, newSequences));

        return found;
    }

    /**
     * Get the size of the group.
     *
     * @return the size of the group.
     */
    public int size()
    {
        return sequencesRef.get().length;
    }
}
