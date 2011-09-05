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

/**
 * Abstraction for claiming a sequence for access to a data structure while tracking dependent {@link Sequence}s
 */
public interface SequenceManager
{
    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    public int getBufferSize();

    /**
     * Set the sequences that will be tracked to prevent the buffer wrapping.
     *
     * This method must be called prior to claiming events in the RingBuffer otherwise
     * a NullPointerException will be thrown.
     *
     * @param sequences to be tracked.
     */
    void setTrackedSequences(Sequence... sequences);

    /**
     * Create a {@link SequenceBarrier} that gates on the the cursor and a list of {@link Sequence}s
     *
     * @param sequencesToTrack this barrier will track
     * @return the barrier gated as required
     */
    public SequenceBarrier newSequenceBarrier(Sequence... sequencesToTrack);


    /**
     * Get the value of the cursor indicating the published sequence.
     *
     * @return value of the cursor for events that have been published.
     */
    long getCursor();

    /**
     * Claim the next event in sequence for publishing to the {@link RingBuffer}
     *
     * @return the claimed sequence
     */
    long nextSequence();

    /**
     * Claim the next batch sequence numbers for publishing to the {@link RingBuffer}
     *
     * @param sequenceBatch to be updated for the batch range.
     * @return the updated sequenceBatch.
     */
    SequenceBatch nextSequenceBatch(SequenceBatch sequenceBatch);

    /**
     * Publish an event back to the {@link RingBuffer} and make it visible to {@link EventProcessor}s
     * @param sequence to be published from the {@link RingBuffer}
     */
    void publish(long sequence);

    /**
     * Publish the batch of events in sequence to the {@link RingBuffer}.
     *
     * @param sequenceBatch to be published.
     */
    void publish(SequenceBatch sequenceBatch);
}
