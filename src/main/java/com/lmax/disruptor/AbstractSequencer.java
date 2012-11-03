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

import static java.util.Arrays.copyOf;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

abstract class AbstractSequencer implements Sequencer
{
    private static final AtomicReferenceFieldUpdater<AbstractSequencer, Sequence[]> sequenceUpdater = 
            AtomicReferenceFieldUpdater.newUpdater(AbstractSequencer.class, Sequence[].class, "gatingSequences");
    protected volatile Sequence[] gatingSequences = new Sequence[0];

    public AbstractSequencer()
    {
        super();
    }

    @Override
    public void setGatingSequences(final Sequence cursor, final Sequence... sequences)
    {
        Sequence[] tempGatingSequences = null;
        long cursorSequence;
        
        do
        {            
            tempGatingSequences = copyOf(gatingSequences, gatingSequences.length + sequences.length);
            cursorSequence = cursor.get();
            
            int index = gatingSequences.length;
            for (Sequence sequence : sequences)
            {
                sequence.set(cursorSequence);
                tempGatingSequences[index++] = sequence;
            }
        }
        while (!sequenceUpdater.compareAndSet(this, gatingSequences, tempGatingSequences));
        
        cursorSequence = cursor.get();
        for (Sequence sequence : tempGatingSequences)
        {
            sequence.set(cursorSequence);
        }
    }
}