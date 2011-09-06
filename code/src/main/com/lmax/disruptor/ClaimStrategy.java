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

import com.lmax.disruptor.util.PaddedAtomicLong;
import com.lmax.disruptor.util.PaddedLong;

import static com.lmax.disruptor.util.Util.getMinimumSequence;

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
     * Ensure sufficient capacity remains in the buffer for the dependent sequences.
     *
     * @param sequence to check is in range
     * @param dependentSequences to be checked for range.
     */
    void ensureCapacity(final long sequence, final Sequence[] dependentSequences);

    /**
     * Is there available capacity in the buffer for the requested sequence.
     *
     * @param sequence to check is in range
     * @param dependentSequences to be checked for range.
     */
    boolean hasAvailableCapacity(final long sequence, final Sequence[] dependentSequences);

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
        private static final int RETRIES = 200;
        private final int bufferSize;
        private final PaddedAtomicLong minGatingSequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
        private final PaddedAtomicLong sequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);

        public MultiThreadedStrategy(final int bufferSize)
        {
            this.bufferSize = bufferSize;
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
        public void ensureCapacity(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                int counter = 100;
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    counter = applyBackPressure(counter);
                }

                minGatingSequence.lazySet(minSequence);
            }
        }

        @Override
        public boolean hasAvailableCapacity(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence = getMinimumSequence(dependentSequences);
                minGatingSequence.set(minSequence);

                if (wrapPoint > minSequence)
                {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void serialisePublishing(final Sequence cursor, final long sequence, final long batchSize)
        {
            final long expectedSequence = sequence - batchSize;
            int counter = RETRIES;
            while (expectedSequence != cursor.get())
            {
                --counter;
                if (counter == 0)
                {
                    counter = RETRIES;
                    Thread.yield();
                }
            }
        }


        private int applyBackPressure(int counter)
        {
            if (counter > 0)
            {
                --counter;
                Thread.yield();
            }
            else
            {
                try
                {
                    counter = RETRIES;
                    Thread.sleep(1L);
                }
                catch (InterruptedException e)
                {
                    // don't care
                }
            }

            return counter;
        }
    }

    /**
     * Optimised strategy can be used when there is a single publisher thread claiming events.
     */
    static final class SingleThreadedStrategy
        implements ClaimStrategy
    {
        private static final int RETRIES = 100;
        private final int bufferSize;
        private final PaddedLong minGatingSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
        private final PaddedLong sequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);

        public SingleThreadedStrategy(final int bufferSize)
        {
            this.bufferSize = bufferSize;
        }

        @Override
        public long incrementAndGet()
        {
            long value = sequence.get() + 1L;
            sequence.set(value);
            return value;
        }

        @Override
        public long incrementAndGet(final int delta)
        {
            long value = sequence.get() + delta;
            sequence.set(value);
            return value;
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence.set(sequence);
        }

        @Override
        public void ensureCapacity(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence;
                int counter = RETRIES;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    counter = applyBackPressure(counter);
                }

                minGatingSequence.set(minSequence);
            }
        }

        @Override
        public boolean hasAvailableCapacity(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence = getMinimumSequence(dependentSequences);
                minGatingSequence.set(minSequence);

                if (wrapPoint > minSequence)
                {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void serialisePublishing(final Sequence cursor, final long sequence, final long batchSize)
        {
        }

        private int applyBackPressure(int counter)
        {
            if (counter > 0)
            {
                --counter;
                Thread.yield();
            }
            else
            {
                try
                {
                    counter = RETRIES;
                    Thread.sleep(1L);
                }
                catch (InterruptedException e)
                {
                    // don't care
                }
            }

            return counter;
        }
    }
}
