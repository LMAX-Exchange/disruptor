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
 * Ring based store of reusable entries containing the data representing an event being exchanged between event publisher and {@link EventProcessor}s.
 *
 * @param <T> implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
public final class RingBuffer<T> extends Sequencer
{
    private final int indexMask;
    private final Object[] entries;

    /**
     * Construct a RingBuffer with the full option set.
     *
     * @param eventFactory to newInstance entries for filling the RingBuffer
     * @param claimStrategy threading strategy for publisher claiming entries in the ring.
     * @param waitStrategy waiting strategy employed by processorsToTrack waiting on entries becoming available.
     *
     * @throws IllegalArgumentException if bufferSize is not a power of 2
     */
    public RingBuffer(final EventFactory<T> eventFactory,
                      final ClaimStrategy claimStrategy,
                      final WaitStrategy waitStrategy)
    {
        super(claimStrategy, waitStrategy);

        if (Integer.bitCount(claimStrategy.getBufferSize()) != 1)
        {
            throw new IllegalArgumentException("bufferSize must be a power of 2");
        }

        indexMask = claimStrategy.getBufferSize() - 1;
        entries = new Object[claimStrategy.getBufferSize()];

        fill(eventFactory);
    }

    /**
     * Construct a RingBuffer with default strategies of:
     * {@link MultiThreadedClaimStrategy} and {@link BlockingWaitStrategy}
     *
     * @param eventFactory to newInstance entries for filling the RingBuffer
     * @param bufferSize of the RingBuffer that will be rounded up to the next power of 2
     */
    public RingBuffer(final EventFactory<T> eventFactory, final int bufferSize)
    {
        this(eventFactory,
             new MultiThreadedClaimStrategy(bufferSize),
             new BlockingWaitStrategy());
    }

    /**
     * Get the event for a given sequence in the RingBuffer.
     *
     * @param sequence for the event
     * @return event for the sequence
     */
    @SuppressWarnings("unchecked")
    public T get(final long sequence)
    {
        return (T)entries[(int)sequence & indexMask];
    }

    private void fill(final EventFactory<T> eventFactory)
    {
        for (int i = 0; i < entries.length; i++)
        {
            entries[i] = eventFactory.newInstance();
        }
    }
}
