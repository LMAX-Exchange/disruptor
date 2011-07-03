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

import java.util.concurrent.atomic.AtomicLong;

/**
 * Strategies employed for claiming the sequence of {@link AbstractEntry}s in the {@link RingBuffer} by producers.
 *
 * The {@link AbstractEntry} index is a the sequence value mod the {@link RingBuffer} capacity.
 */
public interface ClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the {@link AbstractEntry} index to be used for the producer.
     */
    long getAndIncrement();

    /**
     * Set the current sequence value for claiming {@link AbstractEntry} in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(long sequence);

    /**
     * Indicates the threading policy to be applied for claiming {@link AbstractEntry}s by producers to the {@link RingBuffer}
     */
    enum Option
    {
        /** Makes the {@link RingBuffer} thread safe for claiming {@link AbstractEntry}s by multiple producing threads. */
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new MultiThreadedStrategy();
            }
        },

         /** Optimised {@link RingBuffer} for use by single thread claiming {@link AbstractEntry}s as a producer. */
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
     * Strategy to be used when there are multiple producer threads claiming {@link AbstractEntry}s.
     */
    static final class MultiThreadedStrategy
        implements ClaimStrategy
    {
        private final AtomicLong sequence = new AtomicLong(0);

        @Override
        public long getAndIncrement()
        {
            return sequence.getAndIncrement();
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence.set(sequence);
        }
    }

    /**
     * Optimised strategy can be used when there is a single producer thread claiming {@link AbstractEntry}s.
     */
    static final class SingleThreadedStrategy
        implements ClaimStrategy
    {
        private long sequence;

        @Override
        public long getAndIncrement()
        {
            return sequence++;
        }

        @Override
        public void setSequence(final long sequence)
        {
            this.sequence = sequence;
        }
    }
}
