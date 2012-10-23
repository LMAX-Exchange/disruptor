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

interface Sequencer
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1L;

    /**
     * Set the sequences that will gate publishers to prevent the buffer wrapping.
     *
     * This method must be called prior to claiming sequences otherwise
     * a NullPointerException will be thrown.
     * @param cursor 
     *
     * @param sequences to be to be gated on.
     */
    void setGatingSequences(Sequence cursor, final Sequence... sequences);

    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    int getBufferSize();

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     *
     * @param requiredCapacity in the buffer
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    boolean hasAvailableCapacity(final int requiredCapacity);

    /**
     * Claim the next event in sequence for publishing.
     *
     * @return the claimed sequence value
     */
    long next();

    /**
     * Attempt to claim the next event in sequence for publishing.  Will return the
     * number of the slot if there is at least <code>requiredCapacity</code> slots
     * available.
     *
     * @return the claimed sequence value
     * @throws InsufficientCapacityException
     */
    long tryNext() throws InsufficientCapacityException;

    /**
     * Claim a specific sequence when only one publisher is involved.
     *
     * @param sequence to be claimed.
     * @return sequence just claimed.
     */
    long claim(final long sequence);

    /**
     * Get the remaining capacity for this sequencer.
     *
     * @return The number of slots remaining.
     */
    long remainingCapacity();
}