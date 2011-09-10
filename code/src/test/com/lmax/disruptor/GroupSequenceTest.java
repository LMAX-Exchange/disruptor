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

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public final class GroupSequenceTest
{
    @Test
    public void shouldReturnMaxSequenceWhenEmptyGroup()
    {
        final GroupSequence groupSequence = new GroupSequence();
        assertEquals(Long.MAX_VALUE, groupSequence.get());
    }

    @Test
    public void shouldAddOneSequenceToGroup()
    {
        final Sequence sequence = new Sequence(7L);
        final GroupSequence groupSequence = new GroupSequence();

        groupSequence.add(sequence);

        assertEquals(sequence.get(), groupSequence.get());
    }

    @Test
    public void shouldReportTheMinimumSequenceForGroupOfTwo()
    {
        final Sequence sequenceThree = new Sequence(3L);
        final Sequence sequenceSeven = new Sequence(7L);
        final GroupSequence groupSequence = new GroupSequence();

        groupSequence.add(sequenceSeven);
        groupSequence.add(sequenceThree);

        assertEquals(sequenceThree.get(), groupSequence.get());
    }

    @Test
    public void shouldReportSizeOfGroup()
    {
        final GroupSequence groupSequence = new GroupSequence();
        groupSequence.add(new Sequence());
        groupSequence.add(new Sequence());
        groupSequence.add(new Sequence());

        assertEquals(3, groupSequence.size());
    }

    @Test
    public void shouldRemoveSequenceFromGroup()
    {
        final Sequence sequenceThree = new Sequence(3L);
        final Sequence sequenceSeven = new Sequence(7L);
        final GroupSequence groupSequence = new GroupSequence();

        groupSequence.add(sequenceSeven);
        groupSequence.add(sequenceThree);

        assertEquals(sequenceThree.get(), groupSequence.get());

        assertTrue(groupSequence.remove(sequenceThree));
        assertEquals(sequenceSeven.get(), groupSequence.get());
        assertEquals(1, groupSequence.size());
    }

    @Test
    public void shouldSetGroupSequenceToSameValue()
    {
        final Sequence sequenceThree = new Sequence(3L);
        final Sequence sequenceSeven = new Sequence(7L);
        final GroupSequence groupSequence = new GroupSequence();

        groupSequence.add(sequenceSeven);
        groupSequence.add(sequenceThree);

        final long expectedSequence = 11L;
        groupSequence.set(expectedSequence);

        assertEquals(expectedSequence, sequenceThree.get());
        assertEquals(expectedSequence, sequenceSeven.get());
    }
}
