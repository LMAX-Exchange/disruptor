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

/**
 * Coordinates claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
interface Sequencer
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1L;

    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     * @param gatingSequences to gate on
     * @param requiredCapacity in the buffer
     *
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(Sequence[] gatingSequences, final int requiredCapacity);

    /**
     * Claim the next event in sequence for publishing.
     * @param gatingSequences to gate on
     *
     * @return the claimed sequence value
     */
    long next(Sequence[] gatingSequences);

    /**
     * Attempt to claim the next event in sequence for publishing.  Will return the
     * number of the slot if there is at least <code>requiredCapacity</code> slots
     * available.
     * @param gatingSequences to gate on
     *
     * @return the claimed sequence value
     * @throws InsufficientCapacityException
     */
    long tryNext(Sequence[] gatingSequences) throws InsufficientCapacityException;

    /**
     * Get the remaining capacity for this sequencer.
     * @param gatingSequences to gate on
     *
     * @return The number of slots remaining.
     */
    long remainingCapacity(Sequence[] gatingSequences);

    /**
     * Claim a specific sequence.  Only used if initialising the ring buffer to
     * a specific value.
     * 
     * @param sequence The sequence to initialise too.
     */
    void claim(long sequence);
}