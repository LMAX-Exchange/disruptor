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

import static com.lmax.disruptor.Util.ceilingNextPowerOfTwo;

/**
 * Ring based store of reusable events containing the data representing an {@link AbstractEvent} being exchanged between publisher and processorsToTrack.
 *
 * @param <T> AbstractEvent implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class RingBuffer<T extends AbstractEvent>
    implements PublishPort<T>
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1L;

    private final Sequence cursor = new Sequence(INITIAL_CURSOR_VALUE);
    private final int ringModMask;
    private final AbstractEvent[] events;

    private Sequence[] processorSequencesToTrack;

    private final ClaimStrategy claimStrategy;
    private final WaitStrategy waitStrategy;

    /**
     * Construct a RingBuffer with the full option set.
     *
     * @param eventFactory to create {@link AbstractEvent}s for filling the RingBuffer
     * @param size of the RingBuffer that will be rounded up to the next power of 2
     * @param claimStrategyOption threading strategy for publisher claiming {@link AbstractEvent}s in the ring.
     * @param waitStrategyOption waiting strategy employed by processorsToTrack waiting on {@link AbstractEvent}s becoming available.
     */
    public RingBuffer(final EventFactory<T> eventFactory, final int size,
                      final ClaimStrategy.Option claimStrategyOption,
                      final WaitStrategy.Option waitStrategyOption)
    {
        int sizeAsPowerOfTwo = ceilingNextPowerOfTwo(size);
        ringModMask = sizeAsPowerOfTwo - 1;
        events = new AbstractEvent[sizeAsPowerOfTwo];

        claimStrategy = claimStrategyOption.newInstance(sizeAsPowerOfTwo);
        waitStrategy = waitStrategyOption.newInstance();

        fill(eventFactory);
    }

    /**
     * Construct a RingBuffer with default strategies of:
     * {@link ClaimStrategy.Option#MULTI_THREADED} and {@link WaitStrategy.Option#BLOCKING}
     *
     * @param eventFactory to create {@link AbstractEvent}s for filling the RingBuffer
     * @param size of the RingBuffer that will be rounded up to the next power of 2
     */
    public RingBuffer(final EventFactory<T> eventFactory, final int size)
    {
        this(eventFactory, size,
             ClaimStrategy.Option.MULTI_THREADED,
             WaitStrategy.Option.BLOCKING);
    }

    /**
     * Set the processorsToTrack that will be tracked to prevent the ring wrapping.
     *
     * This method must be called prior to claiming events in the RingBuffer otherwise
     * a NullPointerException will be thrown.
     *
     * @param eventProcessors to be tracked.
     */
    public void setTrackedProcessors(final EventProcessor... eventProcessors)
    {
        Sequence[] temp = new Sequence[eventProcessors.length];
        for (int i = 0; i < eventProcessors.length; i++)
        {
            temp[i] = eventProcessors[i].getSequence();
        }

        this.processorSequencesToTrack = temp;
    }

    /**
     * Create a {@link DependencyBarrier} that gates on the RingBuffer and a list of {@link EventProcessor}s
     *
     * @param processorsToTrack this barrier will track
     * @return the barrier gated as required
     */
    public DependencyBarrier newDependencyBarrier(final EventProcessor... processorsToTrack)
    {
        Sequence[] dependentSequences = new Sequence[processorsToTrack.length];
        for (int i = 0; i < processorsToTrack.length; i++)
        {
            dependentSequences[i] = processorsToTrack[i].getSequence();
        }

        return new TrackingDependencyBarrier(waitStrategy, cursor, dependentSequences);
    }

    /**
     * The capacity of the RingBuffer to hold events.
     *
     * @return the size of the RingBuffer.
     */
    public int getCapacity()
    {
        return events.length;
    }

    /**
     * Get the current sequence that is published to the RingBuffer.
     *
     * @return the current published sequence.
     */
    public long getCursor()
    {
        return cursor.get();
    }

    /**
     * Get the {@link AbstractEvent} for a given sequence in the RingBuffer.
     *
     * @param sequence for the {@link AbstractEvent}
     * @return {@link AbstractEvent} for the sequence
     */
    @SuppressWarnings("unchecked")
    public T getEvent(final long sequence)
    {
        return (T)events[(int)sequence & ringModMask];
    }

    @Override
    @SuppressWarnings("unchecked")
    public T nextEvent()
    {
        final long sequence = claimStrategy.incrementAndGet();
        claimStrategy.ensureProcessorsAreInRange(sequence, processorSequencesToTrack);

        AbstractEvent event = events[(int)sequence & ringModMask];
        event.setSequence(sequence);

        return (T)event;
    }

    @Override
    public void publish(final T event)
    {
        publish(event.getSequence(), 1);
    }

    @Override
    public SequenceBatch nextEvents(final SequenceBatch sequenceBatch)
    {
        final long sequence = claimStrategy.incrementAndGet(sequenceBatch.getSize());
        sequenceBatch.setEnd(sequence);
        claimStrategy.ensureProcessorsAreInRange(sequence, processorSequencesToTrack);

        for (long i = sequenceBatch.getStart(), end = sequenceBatch.getEnd(); i <= end; i++)
        {
            AbstractEvent event = events[(int)i & ringModMask];
            event.setSequence(i);
        }

        return sequenceBatch;
    }

    @Override
    public void publish(final SequenceBatch sequenceBatch)
    {
        publish(sequenceBatch.getEnd(), sequenceBatch.getSize());
    }

    /**
     * Claim a specific sequence in the {@link RingBuffer} when only one publisher is involved.
     *
     * @param sequence to be claimed.
     * @return the claimed {@link AbstractEvent}
     */
    @SuppressWarnings("unchecked")
    public T publishEventAtSequence(final long sequence)
    {
        claimStrategy.ensureProcessorsAreInRange(sequence, processorSequencesToTrack);
        AbstractEvent event = events[(int)sequence & ringModMask];
        event.setSequence(sequence);

        return (T)event;
    }

    /**
     * Publish an event back to the {@link RingBuffer} to make it visible to {@link EventProcessor}s.
     * Only use this method when forcing a sequence and you are sure only one publisher exists.
     * This will cause the {@link RingBuffer} to advance the {@link RingBuffer#getCursor()} to this sequence.
     *
     * @param event to be published from to the {@link RingBuffer}
     */
    public void publishWithForce(final T event)
    {
        long sequence = event.getSequence();
        claimStrategy.setSequence(sequence);
        cursor.set(sequence);
        waitStrategy.signalAll();
    }

    private void publish(final long sequence, final long batchSize)
    {
        claimStrategy.serialisePublishing(cursor, sequence, batchSize);
        cursor.set(sequence);
        waitStrategy.signalAll();
    }

    private void fill(final EventFactory<T> eventFactory)
    {
        for (int i = 0; i < events.length; i++)
        {
            events[i] = eventFactory.create();
        }
    }
}
