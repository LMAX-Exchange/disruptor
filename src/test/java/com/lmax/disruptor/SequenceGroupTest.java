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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.lmax.disruptor.support.TestEvent;

public final class SequenceGroupTest
{

    private static final int BUFFER_SIZE = 32;
    private static final long HIGHER_VALUE = 7L;    
    private static final long LOWER_VALUE = 3L;

    private Sequence lowerSequece;
    private Sequence higherSequence;

    @Before
    public void setUp() {
        lowerSequece = new Sequence(LOWER_VALUE);
        higherSequence = new Sequence(HIGHER_VALUE);
    }
    
    @Test
    public void shouldReturnMaxSequenceWhenEmptyGroup()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();
        assertEquals(Long.MAX_VALUE, sequenceGroup.get());
    }

    @Test
    public void shouldAddOneSequenceToGroup()
    {
        final Sequence sequence = new Sequence(7L);
        final SequenceGroup sequenceGroup = new SequenceGroup();
        sequenceGroup.add(sequence);

        assertEquals(sequence.get(), sequenceGroup.get());
    }

    @Test
    public void shouldNotFailIfTryingToRemoveNotExistingSequence() throws Exception
    {
        SequenceGroup group = new SequenceGroup();
        group.add(new Sequence());
        group.add(new Sequence());
        group.remove(new Sequence());
    }

    @Test
    public void shouldReportTheMinimumSequenceForGroupOfTwo()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();
        sequenceGroup.add(higherSequence);
        sequenceGroup.add(lowerSequece);

        assertEquals(lowerSequece.get(), sequenceGroup.get());
    }

    @Test
    public void shouldReportSizeOfGroup()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();        
        sequenceGroup.add(new Sequence());
        sequenceGroup.add(new Sequence());
        sequenceGroup.add(new Sequence());
        final int sequenceGroupSize = 3;

        assertEquals(sequenceGroupSize, sequenceGroup.size());
    }

    @Test
    public void shouldRemoveSequenceFromGroup()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();
        final int sequenceGroupSizeAfterRemove = 1;

        sequenceGroup.add(higherSequence);
        sequenceGroup.add(lowerSequece);

        assertEquals(lowerSequece.get(), sequenceGroup.get());

        assertTrue(sequenceGroup.remove(lowerSequece));
        assertEquals(higherSequence.get(), sequenceGroup.get());
        assertEquals(sequenceGroupSizeAfterRemove, sequenceGroup.size());
    }

    @Test
    public void shouldRemoveSequenceFromGroupWhereItBeenAddedMultipleTimes()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();
        final int sequenceGroupSizeAfterRemove = 1;

        sequenceGroup.add(lowerSequece);
        sequenceGroup.add(higherSequence);
        sequenceGroup.add(lowerSequece);

        assertEquals(lowerSequece.get(), sequenceGroup.get());

        assertTrue(sequenceGroup.remove(lowerSequece));
        assertEquals(higherSequence.get(), sequenceGroup.get());
        assertEquals(sequenceGroupSizeAfterRemove, sequenceGroup.size());
    }

    @Test
    public void shouldSetGroupSequenceToSameValue()
    {
        final SequenceGroup sequenceGroup = new SequenceGroup();

        sequenceGroup.add(higherSequence);
        sequenceGroup.add(lowerSequece);

        final long expectedSequence = 11L;
        sequenceGroup.set(expectedSequence);

        assertEquals(expectedSequence, lowerSequece.get());
        assertEquals(expectedSequence, higherSequence.get());
    }

    @Test
    public void shouldAddWhileRunning() throws Exception
    {
        RingBuffer<TestEvent> ringBuffer = RingBuffer.createSingleProducer(TestEvent.EVENT_FACTORY, BUFFER_SIZE);
        final SequenceGroup sequenceGroup = new SequenceGroup();
        sequenceGroup.add(higherSequence);

        for (int i = 0; i < 11; i++)
        {
            ringBuffer.publish(ringBuffer.next());
        }

        sequenceGroup.addWhileRunning(ringBuffer, lowerSequece);
        assertThat(lowerSequece.get(), is(10L));
    }
}
