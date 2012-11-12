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

class SequenceGroups
{
    static <T> void addSequences(final T holder,
                                 final AtomicReferenceFieldUpdater<T, Sequence[]> updater,
                                 final RingBuffer<?> cursor,
                                 final Sequence... sequencesToAdd)
    {
        long cursorSequence;
        Sequence[] updatedSequences;
        Sequence[] currentSequences;
        
        do
        {
            currentSequences = updater.get(holder);
            updatedSequences = copyOf(currentSequences, currentSequences.length + sequencesToAdd.length);
            cursorSequence = cursor.getCursor();
            
            int index = currentSequences.length;
            for (Sequence sequence : sequencesToAdd)
            {
                sequence.set(cursorSequence);
                updatedSequences[index++] = sequence;
            }
        }
        while (!updater.compareAndSet(holder, currentSequences, updatedSequences));
        
        cursorSequence = cursor.getCursor();
        for (Sequence sequence : sequencesToAdd)
        {
            sequence.set(cursorSequence);
        }
    }

}
