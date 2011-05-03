package com.lmax.disruptor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementations of this strategy can handled the necessary threading requirements
 * for claiming {@link Entry}s in the {@link RingBuffer}.
 *
 * The {@link Entry} index is a the sequence value mod the {@link RingBuffer} capacity.
 */
public interface ClaimStrategy
{
    /**
     * Claim the next sequence index in the {@link RingBuffer} and increment.
     *
     * @return the {@link Entry} index to be used for the producer.
     */
    long getAndIncrement();

    /**
     * Set the current sequence value for claiming {@link Entry} in the {@link RingBuffer}
     *
     * @param sequence to be set as the current value.
     */
    void setSequence(long sequence);

    /**
     * Wait for the current claim to reach a given sequence.
     *
     * @param sequence to wait for.
     * @param ringBuffer on which to wait forCursor
     */
    void waitForCursor(long sequence, RingBuffer ringBuffer);

    /**
     * Indicates the threading policy to be applied for claiming {@link Entry}s by producers to the {@link com.lmax.disruptor.RingBuffer}
     */
    enum Option
    {
        /** Makes the {@link RingBuffer} thread safe for claiming {@link Entry}s by multiple producing threads. */
        MULTI_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new MultiThreadedStrategy();
            }
        },

         /** Optimised {@link RingBuffer} from use by single thread claiming {@link Entry}s as a  producer. */
        SINGLE_THREADED
        {
            @Override
            public ClaimStrategy newInstance()
            {
                return new SingleThreadedStrategy();
            }
        };

        /**
         * Used by the {@link com.lmax.disruptor.RingBuffer} as a polymorphic constructor.
         *
         * @return a new instance of the ClaimStrategy
         */
        abstract ClaimStrategy newInstance();
    }

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

        @Override
        public void waitForCursor(final long sequence, final RingBuffer ringBuffer)
        {
            while (ringBuffer.getCursor() != sequence)
            {
                // busy spin
            }
        }
    }

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

        @Override
        public void waitForCursor(final long sequence, final RingBuffer ringBuffer)
        {
            // no op for this class
        }
    }
}
