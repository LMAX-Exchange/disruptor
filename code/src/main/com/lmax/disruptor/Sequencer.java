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
 * Coordinator for claiming sequences for access to a data structure while tracking dependent {@link Sequence}s
 */
public class Sequencer
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1L;

    private final int bufferSize;

    private final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private Sequence[] gatingSequences;

    private final ClaimStrategy claimStrategy;
    private final WaitStrategy waitStrategy;

    /**
     * Construct a Sequencer with the selected strategies.
     *
     * @param bufferSize over which sequences are valid.
     * @param claimStrategyOption for those claiming sequences.
     * @param waitStrategyOption for those waiting on sequences.
     */
    public Sequencer(final int bufferSize,
                     final ClaimStrategy.Option claimStrategyOption,
                     final WaitStrategy.Option waitStrategyOption)
    {
        this.claimStrategy = claimStrategyOption.newInstance(bufferSize);
        this.waitStrategy = waitStrategyOption.newInstance();
        this.bufferSize = bufferSize;
    }

    /**
     * Set the sequences that will gate publishers to prevent the buffer wrapping.
     *
     * This method must be called prior to claiming sequences otherwise
     * a NullPointerException will be thrown.
     *
     * @param sequences to be to be gated on.
     */
    public void setGatingSequences(final Sequence... sequences)
    {
        this.gatingSequences = sequences;
    }

    /**
     * Create a {@link SequenceBarrier} that gates on the the cursor and a list of {@link Sequence}s
     *
     * @param sequencesToTrack this barrier will track
     * @return the barrier gated as required
     */
    public SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
    {
        return new ProcessingSequenceBarrier(waitStrategy, cursor, sequencesToTrack);
    }

    /**
     * Create a new {@link SequenceBatch} that is the minimum of the requested size
     * and the buffer size.
     *
     * @param size for the batch
     * @return the new {@link SequenceBatch}
     */
    public SequenceBatch newSequenceBatch(final int size)
    {
        return new SequenceBatch(Math.min(size, bufferSize));
    }

    /**
     * The capacity of the data structure to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    public int getBufferSize()
    {
        return bufferSize;
    }

    /**
     * Get the value of the cursor indicating the published sequence.
     *
     * @return value of the cursor for events that have been published.
     */
    public long getCursor()
    {
        return cursor.get();
    }

    /**
     * Has the buffer got capacity to allocate another sequence.  This is a concurrent
     * method so the response should only be taken as an indication of available capacity.
     *
     * @return true if the buffer has the capacity to allocate the next sequence otherwise false.
     */
    public boolean hasAvailableCapacity()
    {
        return claimStrategy.hasAvailableCapacity(gatingSequences);
    }

    /**
     * Claim the next event in sequence for publishing to the {@link RingBuffer}
     *
     * @return the claimed sequence
     */
    public long next()
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        return claimStrategy.incrementAndGet(gatingSequences);
    }

    /**
     * Claim the next batch of sequence numbers for publishing.
     *
     * @param sequenceBatch to be updated for the batch range.
     * @return the updated sequenceBatch.
     */
    public SequenceBatch next(final SequenceBatch sequenceBatch)
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        final long sequence = claimStrategy.incrementAndGet(sequenceBatch.getSize(), gatingSequences);
        sequenceBatch.setEnd(sequence);
        return sequenceBatch;
    }

    /**
     * Claim a specific sequence when only one publisher is involved.
     *
     * @param sequence to be claimed.
     * @return sequence just claimed.
     */
    public long claim(final long sequence)
    {
        if (null == gatingSequences)
        {
            throw new NullPointerException("gatingSequences must be set before claiming sequences");
        }

        claimStrategy.setSequence(sequence, gatingSequences);

        return sequence;
    }

    /**
     * Publish an event and make it visible to {@link EventProcessor}s
     *
     * @param sequence to be published
     */
    public void publish(final long sequence)
    {
        publish(sequence, 1);
    }

    /**
     * Publish the batch of events in sequence.
     *
     * @param sequenceBatch to be published.
     */
    public void publish(final SequenceBatch sequenceBatch)
    {
        publish(sequenceBatch.getEnd(), sequenceBatch.getSize());
    }

    /**
     * Force the publication of a cursor sequence.
     *
     * Only use this method when forcing a sequence and you are sure only one publisher exists.
     * This will cause the cursor to advance to this sequence.
     *
     * @param sequence which is to be forced for publication.
     */
    public void forcePublish(final long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    private void publish(final long sequence, final long batchSize)
    {
        claimStrategy.serialisePublishing(sequence, cursor, batchSize);
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }
}
