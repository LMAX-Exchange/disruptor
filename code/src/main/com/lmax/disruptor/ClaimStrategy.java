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

import java.util.concurrent.atomic.AtomicLongArray;

/**
 * Strategies employed for claiming the sequence of {@link AbstractEvent}s in the {@link RingBuffer} by publishers.
 */
public interface ClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the {@link AbstractEvent} index to be used for the publisher.
     */
    long incrementAndGet();

    /**
     * Increment by a delta and get the result.
     *
     * @param delta to increment by.
     * @return the result after incrementing.
     */
    long incrementAndGet(int delta);

    /**
     * Set the current sequence value for claiming {@link AbstractEvent} in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(long sequence);

    /**
     * Indicates the threading policy to be applied for claiming {@link AbstractEvent}s by publisher to the {@link RingBuffer}
     */
    enum Option
    {
        /** Makes the {@link RingBuffer} thread safe for claiming {@link AbstractEvent}s by multiple producing threads. */
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new MultiThreadedStrategy();
            }
        },

         /** Optimised {@link RingBuffer} for use by single thread claiming {@link AbstractEvent}s as a publisher. */
        SINGLE_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new SingleThreadedStrategy();
            }
        };

        /**
         * Used by the {@link RingBuffer} as a polymorphic constructor.
         *
         * @return a new instance of the ClaimStrategy
         */
        abstract ClaimStrategy newInstance();
    }

    /**
     * Strategy to be used when there are multiple publisher threads claiming {@link AbstractEvent}s.
     */
    static final class MultiThreadedStrategy
        implements ClaimStrategy
    {
        private final AtomicLongArray sequence = new AtomicLongArray(5); // cache line padded

        public MultiThreadedStrategy()
        {
            sequence.set(0, RingBuffer.INITIAL_CURSOR_VALUE);
        }

        @Override
        public long incrementAndGet()
        {
            return sequence.incrementAndGet(0);
        }

        @Override
        public long incrementAndGet(final int delta)
        {
            return sequence.addAndGet(0, delta);
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence.lazySet(0, sequence);
        }
    }

    /**
     * Optimised strategy can be used when there is a single publisher thread claiming {@link AbstractEvent}s.
     */
    static final class SingleThreadedStrategy
        implements ClaimStrategy
    {
        private final long[] sequence = new long[5]; // cache line padded

        public SingleThreadedStrategy()
        {
            sequence[0] = RingBuffer.INITIAL_CURSOR_VALUE;
        }

        @Override
        public long incrementAndGet()
        {
            return ++sequence[0];
        }

        @Override
        public long incrementAndGet(final int delta)
        {
            sequence[0] += delta;
            return sequence[0];
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence[0] = sequence;
        }
    }
}
