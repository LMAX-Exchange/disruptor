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
 * Strategy contract for claiming the sequence of events in the {@link Sequencer} by event publishers.
 */
public interface ClaimStrategy
{
    /**
     * Get the size of the data structure used to buffer events.
     *
     * @return size of the underlying buffer.
     */
    int getBufferSize();

    /**
     * Get the current claimed sequence.
     *
     * @return the current claimed sequence.
     */
    long getSequence();

    /**
     * Is there available capacity in the buffer for the requested sequence.
     *
     * @param availableCapacity remaining in the buffer.
     * @param dependentSequences to be checked for range.
     * @return true if the buffer has capacity for the requested sequence.
     */
    boolean hasAvailableCapacity(final int availableCapacity, final Sequence[] dependentSequences);

    /**
     * Claim the next sequence in the {@link Sequencer}.
     * The caller should be held up until the claimed sequence is available by tracking the dependentSequences.
     *
     * @param dependentSequences to be checked for range.
     * @return the index to be used for the publishing.
     */
    long incrementAndGet(final Sequence[] dependentSequences);

    /**
     * Increment sequence by a delta and get the result.
     * The caller should be held up until the claimed sequence batch is available by tracking the dependentSequences.
     *
     * @param delta to increment by.
     * @param dependentSequences to be checked for range.
     * @return the result after incrementing.
     */
    long incrementAndGet(final int delta, final Sequence[] dependentSequences);

    /**
     * Set the current sequence value for claiming an event in the {@link Sequencer}
     * The caller should be held up until the claimed sequence is available by tracking the dependentSequences.
     *
     * @param dependentSequences to be checked for range.
     * @param sequence to be set as the current value.
     */
    void setSequence(final long sequence, final Sequence[] dependentSequences);

    /**
     * Serialise publishers in sequence and set cursor to latest available sequence.
     *
     * @param sequence sequence to be applied
     * @param cursor to serialise against.
     * @param batchSize of the sequence.
     */
    void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize);
}
