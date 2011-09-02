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

import static com.lmax.disruptor.Util.getMinimumSequence;

/**
 * Strategies employed for claiming the sequence of events in the {@link RingBuffer} by publishers.
 */
public interface ClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the event index to be used for the publisher.
     */
    long incrementAndGet();

    /**
     * Increment by a delta and get the result.
     *
     * @param delta to increment by.
     * @return the result after incrementing.
     */
    long incrementAndGet(final int delta);

    /**
     * Set the current sequence value for claiming an event in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(final long sequence);

    /**
     * Ensure dependent processors are in range without over taking them for the buffer size.
     *
     * @param sequence to check is in range
     * @param dependentSequences to be checked for range.
     */
    void ensureProcessorsAreInRange(final long sequence, final Sequence[] dependentSequences);

    /**
     * Serialise publishing in sequence.
     *
     * @param cursor to serialise against.
     * @param sequence sequence to be applied
     * @param batchSize of the sequence.
     */
    void serialisePublishing(final Sequence cursor, final long sequence, final long batchSize);

    /**
     * Indicates the threading policy to be applied for claiming events by publisher to the {@link RingBuffer}
     */
    enum Option
    {
        /** Makes the {@link RingBuffer} thread safe for claiming events by multiple producing threads. */
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance(final int bufferSize)
            {
                return new MultiThreadedStrategy(bufferSize);
            }
        },

         /** Optimised {@link RingBuffer} for use by single thread claiming events as a publisher. */
        SINGLE_THREADED
        {
            @Override
            public ClaimStrategy newInstance(final int bufferSize)
            {
                return new SingleThreadedStrategy(bufferSize);
            }
        };

        /**
         * Used by the {@link RingBuffer} as a polymorphic constructor.
         *
         * @param bufferSize of the {@link RingBuffer} for events.
         * @return a new instance of the ClaimStrategy
         */
        abstract ClaimStrategy newInstance(final int bufferSize);
    }

    /**
     * Strategy to be used when there are multiple publisher threads claiming events.
     */
    static final class MultiThreadedStrategy
        implements ClaimStrategy
    {
        private final int bufferSize;
        private final PaddedAtomicLong sequence = new PaddedAtomicLong();
        private final PaddedAtomicLong minProcessorSequence = new PaddedAtomicLong();

        public MultiThreadedStrategy(final int bufferSize)
        {
            this.bufferSize = bufferSize;
            sequence.lazySet(RingBuffer.INITIAL_CURSOR_VALUE);
            minProcessorSequence.lazySet(RingBuffer.INITIAL_CURSOR_VALUE);
        }

        @Override
        public long incrementAndGet()
        {
            return sequence.incrementAndGet();
        }

        @Override
        public long incrementAndGet(final int delta)
        {
            return sequence.addAndGet(delta);
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence.lazySet(sequence);
        }

        @Override
        public void ensureProcessorsAreInRange(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minProcessorSequence.get())
            {
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    Thread.yield();
                }

                minProcessorSequence.lazySet(minSequence);
            }
        }

        @Override
        public void serialisePublishing(final Sequence cursor, final long sequence, final long batchSize)
        {
            final long expectedSequence = sequence - batchSize;
            int counter = 1000;
            while (expectedSequence != cursor.get())
            {
                if (0 == --counter)
                {
                    counter = 1000;
                    Thread.yield();
                }
            }
        }
    }

    /**
     * Optimised strategy can be used when there is a single publisher thread claiming events.
     */
    static final class SingleThreadedStrategy
        implements ClaimStrategy
    {
        private final int SEQ_INDEX = 7;
        private final int bufferSize;
        private final long[] sequence = new long[15]; // cache line padded
        private long minProcessorSequence = RingBuffer.INITIAL_CURSOR_VALUE;

        public SingleThreadedStrategy(final int bufferSize)
        {
            this.bufferSize = bufferSize;
            sequence[SEQ_INDEX] = RingBuffer.INITIAL_CURSOR_VALUE;
        }

        @Override
        public long incrementAndGet()
        {
            long value = sequence[SEQ_INDEX] + 1L;
            sequence[SEQ_INDEX] = value;
            return value;
        }

        @Override
        public long incrementAndGet(final int delta)
        {
            long value = sequence[SEQ_INDEX] + delta;
            sequence[SEQ_INDEX] = value;
            return value;
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence[SEQ_INDEX] = sequence;
        }

        @Override
        public void ensureProcessorsAreInRange(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minProcessorSequence)
            {
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    Thread.yield();
                }

                minProcessorSequence = minSequence;
            }
        }

        @Override
        public void serialisePublishing(final Sequence cursor, final long sequence, final long batchSize)
        {
        }
    }
}
