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

import com.lmax.disruptor.util.MutableLong;
import com.lmax.disruptor.util.PaddedAtomicLong;
import com.lmax.disruptor.util.PaddedLong;

import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.locks.LockSupport;

import static com.lmax.disruptor.util.Util.getMinimumSequence;

/**
 * Strategy contract for claiming the sequence of events in the {@link Sequencer} by publishers.
 */
public interface ClaimStrategy
{
    /**
     * Is there available capacity in the buffer for the requested sequence.
     *
     * @param dependentSequences to be checked for range.
     * @return true if the buffer has capacity for the requested sequence.
     */
    boolean hasAvailableCapacity(final Sequence[] dependentSequences);

    /**
     * Claim the next sequence in the {@link Sequencer}.
     *
     * @param dependentSequences to be checked for range.
     * @return the index to be used for the publishing.
     */
    long incrementAndGet(final Sequence[] dependentSequences);

    /**
     * Increment sequence by a delta and get the result.
     *
     * @param delta to increment by.
     * @param dependentSequences to be checked for range.
     * @return the result after incrementing.
     */
    long incrementAndGet(final int delta, final Sequence[] dependentSequences);

    /**
     * Set the current sequence value for claiming an event in the {@link Sequencer}
     *
     * @param dependentSequences to be checked for range.
     * @param sequence to be set as the current value.
     */
    void setSequence(final long sequence, final Sequence[] dependentSequences);

    /**
     * Serialise publishing in sequence and set cursor to latest available sequence.
     *
     * @param sequence sequence to be applied
     * @param cursor to serialise against.
     * @param batchSize of the sequence.
     */
    void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize);

    /**
     * Indicates the threading policy to be applied for claiming events by publisher to the {@link Sequencer}
     */
    enum Option
    {
        /** Makes the {@link Sequencer} thread safe for claiming events by multiple producing threads. */
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance(final int bufferSize)
            {
                return new MultiThreadedStrategy(bufferSize);
            }
        },

         /** Optimised {@link Sequencer} for use by single thread claiming events as a publisher. */
        SINGLE_THREADED
        {
            @Override
            public ClaimStrategy newInstance(final int bufferSize)
            {
                return new SingleThreadedStrategy(bufferSize);
            }
        };

        /**
         * Used by the {@link Sequencer} as a polymorphic constructor.
         *
         * @param bufferSize of the {@link Sequencer} for events.
         * @return a new instance of the ClaimStrategy
         */
        abstract ClaimStrategy newInstance(final int bufferSize);
    }

    /**
     * Strategy to be used when there are multiple publisher threads claiming sequences.
     */
    public static final class MultiThreadedStrategy
        implements ClaimStrategy
    {
        private final int bufferSize;
        private final int indexMask;
        private final AtomicLongArray pendingPublications;
        private final PaddedAtomicLong claimSequence = new PaddedAtomicLong(Sequencer.INITIAL_CURSOR_VALUE);
        private final PaddedAtomicLong csLock = new PaddedAtomicLong(0L);

        private final ThreadLocal<MutableLong> minGatingSequenceThreadLocal = new ThreadLocal<MutableLong>()
        {
            @Override
            protected MutableLong initialValue()
            {
                return new MutableLong(Sequencer.INITIAL_CURSOR_VALUE);
            }
        };

        public MultiThreadedStrategy(final int bufferSize)
        {
            if (Integer.bitCount(bufferSize) != 1)
            {
                throw new IllegalArgumentException("bufferSize must be a power of 2");
            }

            this.bufferSize = bufferSize;
            indexMask = bufferSize - 1;
            pendingPublications = new AtomicLongArray(bufferSize);
            for (int i = 0, size = pendingPublications.length(); i < size; i++)
            {
                pendingPublications.lazySet(i, Sequencer.INITIAL_CURSOR_VALUE);
            }
        }

        @Override
        public boolean hasAvailableCapacity(final Sequence[] dependentSequences)
        {
            final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
            final long wrapPoint = (claimSequence.get() + 1L) - bufferSize;
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
        public long incrementAndGet(final Sequence[] dependentSequences)
        {
            final MutableLong minGatingSequence = minGatingSequenceThreadLocal.get();
            waitForCapacity(dependentSequences, minGatingSequence);

            final long nextSequence = claimSequence.incrementAndGet();
            waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequence);

            return nextSequence;
        }

        @Override
        public long incrementAndGet(final int delta, final Sequence[] dependentSequences)
        {
            final long nextSequence = claimSequence.addAndGet(delta);
            waitForFreeSlotAt(nextSequence, dependentSequences, minGatingSequenceThreadLocal.get());

            return nextSequence;
        }

        @Override
        public void setSequence(final long sequence, final Sequence[] dependentSequences)
        {
            claimSequence.lazySet(sequence);
            waitForFreeSlotAt(sequence, dependentSequences, minGatingSequenceThreadLocal.get());
        }

        @Override
        public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
        {
            final long expectedSequence = sequence - batchSize;
            if (expectedSequence == cursor.get())
            {
                cursor.set(sequence);
                if (sequence == claimSequence.get())
                {
                    return;
                }
            }
            else
            {
                for (long i = expectedSequence + 1; i <= sequence; i++)
                {
                    pendingPublications.set((int)i & indexMask, i);
                }
            }

            if (csLock.compareAndSet(0L, 1L))
            {
                long initialCursor = cursor.get();
                long currentCursor = initialCursor;

                while (currentCursor < claimSequence.get())
                {
                    long nextSequence = currentCursor + 1L;
                    if (nextSequence != pendingPublications.get((int)nextSequence & indexMask))
                    {
                        break;
                    }

                    currentCursor = nextSequence;
                }

                if (currentCursor > initialCursor)
                {
                    cursor.set(currentCursor);
                }

                csLock.set(0L);
            }
        }

        private void waitForCapacity(final Sequence[] dependentSequences, final MutableLong minGatingSequence)
        {
            final long wrapPoint = (claimSequence.get() + 1L) - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    LockSupport.parkNanos(1000L);
                }

                minGatingSequence.set(minSequence);
            }
        }

        private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences, final MutableLong minGatingSequence)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    LockSupport.parkNanos(1000L);
                }

                minGatingSequence.set(minSequence);
            }
        }
    }

    /**
     * Optimised strategy can be used when there is a single publisher thread claiming sequences.
     */
    public static final class SingleThreadedStrategy
        implements ClaimStrategy
    {
        private final int bufferSize;
        private final PaddedLong minGatingSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);
        private final PaddedLong claimSequence = new PaddedLong(Sequencer.INITIAL_CURSOR_VALUE);

        public SingleThreadedStrategy(final int bufferSize)
        {
            this.bufferSize = bufferSize;
        }

        @Override
        public boolean hasAvailableCapacity(final Sequence[] dependentSequences)
        {
            final long wrapPoint = (claimSequence.get() + 1L) - bufferSize;
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
        public long incrementAndGet(final Sequence[] dependentSequences)
        {
            long nextSequence = claimSequence.get() + 1L;
            claimSequence.set(nextSequence);
            waitForFreeSlotAt(nextSequence, dependentSequences);

            return nextSequence;
        }

        @Override
        public long incrementAndGet(final int delta, final Sequence[] dependentSequences)
        {
            long nextSequence = claimSequence.get() + delta;
            claimSequence.set(nextSequence);
            waitForFreeSlotAt(nextSequence, dependentSequences);

            return nextSequence;
        }

        @Override
        public void setSequence(final long sequence, final Sequence[] dependentSequences)
        {
            claimSequence.set(sequence);
            waitForFreeSlotAt(sequence, dependentSequences);
        }

        @Override
        public void serialisePublishing(final long sequence, final Sequence cursor, final int batchSize)
        {
            cursor.set(sequence);
        }

        private void waitForFreeSlotAt(final long sequence, final Sequence[] dependentSequences)
        {
            final long wrapPoint = sequence - bufferSize;
            if (wrapPoint > minGatingSequence.get())
            {
                long minSequence;
                while (wrapPoint > (minSequence = getMinimumSequence(dependentSequences)))
                {
                    LockSupport.parkNanos(1000L);
                }

                minGatingSequence.set(minSequence);
            }
        }
    }
}
