package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.AlertException.ALERT_EXCEPTION;
import static com.lmax.disruptor.Util.ceilingNextPowerOfTwo;
import static com.lmax.disruptor.Util.getMinimumSequence;

/**
 * Ring based store of reusable entries containing the data representing an {@link Entry} being exchanged between producers and consumers.
 *
 * @param <T> Entry implementation storing the data for sharing during exchange or parallel coordination of an event.
 */
@SuppressWarnings("unchecked")
public final class RingBuffer<T extends Entry>
{
    /** Set to -1 as sequence starting point */
    public static final long INITIAL_CURSOR_VALUE = -1;
    private volatile long cursor = INITIAL_CURSOR_VALUE;

    private final Object[] entries;
    private final int ringModMask;

    private final CommitCallback claimNextCallback = new ClaimNextCommitCallback();
    private final CommitCallback claimSequenceCallback = new SetSequenceCommitCallback();

    private final ClaimStrategy claimStrategy;
    private final WaitStrategy waitStrategy;

    /**
     * Construct a RingBuffer with the full option set.
     *
     * @param entryFactory to create {@link Entry}s for filling the RingBuffer
     * @param size of the RingBuffer that will be rounded up to the next power of 2
     * @param claimStrategyOption threading strategy for producers claiming {@link Entry}s in the ring.
     * @param waitStrategyOption waiting strategy employed by consumers waiting on {@link Entry}s becoming available.
     */
    public RingBuffer(final EntryFactory<T> entryFactory, final int size,
                      final ClaimStrategy.Option claimStrategyOption,
                      final WaitStrategy.Option waitStrategyOption)
    {
        int sizeAsPowerOfTwo = ceilingNextPowerOfTwo(size);
        ringModMask = sizeAsPowerOfTwo - 1;
        entries = new Object[sizeAsPowerOfTwo];

        claimStrategy = claimStrategyOption.newInstance();
        waitStrategy = waitStrategyOption.newInstance();

        fill(entryFactory);
    }

    /**
     * Construct a RingBuffer with default strategies of:
     * {@link ClaimStrategy.Option#MULTI_THREADED} and {@link WaitStrategy.Option#BLOCKING}
     *
     * @param entryFactory to create {@link Entry}s for filling the RingBuffer
     * @param size of the RingBuffer that will be rounded up to the next power of 2
     */
    public RingBuffer(final EntryFactory<T> entryFactory, final int size)
    {
        this(entryFactory, size,
             ClaimStrategy.Option.MULTI_THREADED,
             WaitStrategy.Option.BLOCKING);
    }

    /**
     * Create a {@link ConsumerBarrier} that gates on the RingBuffer and a list of {@link Consumer}s
     *
     * @param consumers this barrier will track
     * @return the barrier gated as required
     */
    public ConsumerBarrier<T> createConsumerBarrier(final Consumer... consumers)
    {
        return new RingBufferConsumerBarrier<T>(this, waitStrategy, consumers);
    }

    /**
     * Create a {@link ProducerBarrier} on this RingBuffer that tracks dependent {@link Consumer}s.
     *
     * The bufferReserve should be at least the number of producing threads.
     *
     * @param bufferReserve size of of the buffer to be reserved.
     * @param consumers to be tracked to prevent wrapping.
     * @return a {@link ProducerBarrier} with the above configuration.
     */
    public ProducerBarrier<T> createProducerBarrier(final int bufferReserve, final Consumer... consumers)
    {
        return new RingBufferProducerBarrier<T>(this, bufferReserve, consumers);
    }

    /**
     * The capacity of the RingBuffer to hold entries.
     *
     * @return the size of the RingBuffer.
     */
    public int getCapacity()
    {
        return entries.length;
    }

    /**
     * Get the current sequence that producers have committed to the RingBuffer.
     *
     * @return the current committed sequence.
     */
    public long getCursor()
    {
        return cursor;
    }

    /**
     * Get the entry for a given sequence from the RingBuffer
     *
     * @param sequence for the entry.
     * @return entry matching the sequence.
     */
    T getEntry(final long sequence)
    {
        return (T)entries[(int)sequence & ringModMask];
    }


    private void fill(final EntryFactory<T> entryEntryFactory)
    {
        for (int i = 0; i < entries.length; i++)
        {
            entries[i] = entryEntryFactory.create();
        }
    }

    private T claimNext()
    {
        long sequence = claimStrategy.getAndIncrement();
        T entry = (T)entries[(int)sequence & ringModMask];
        entry.setSequence(sequence, claimNextCallback);

        return entry;
    }

    private T claimSequence(final long sequence)
    {
        T entry = (T)entries[(int)sequence & ringModMask];
        entry.setSequence(sequence, claimSequenceCallback);

        return entry;
    }

    /**
     * Callback to be used when claiming {@link Entry}s in sequence and cursor is catching up with claim
     * for notifying the the consumers of progress. This will busy spin on the commit until previous
     * producers have committed lower sequence entries.
     */
    private final class ClaimNextCommitCallback implements CommitCallback
    {
        public void commit(final long sequence)
        {
            claimStrategy.waitForCursor(sequence - 1L, RingBuffer.this);
            cursor = sequence;
            waitStrategy.signalAll();
        }
    }

    /**
     * Callback to be used when claiming {@link Entry}s and the cursor is explicitly set by the producer when you are sure only one
     * producer exists.
     */
    private final class SetSequenceCommitCallback implements CommitCallback
    {
        public void commit(final long sequence)
        {
            claimStrategy.setSequence(sequence + 1L);
            cursor = sequence;
            waitStrategy.signalAll();
        }
    }

    /**
     * ConsumerBarrier handed out for gating consumers of the RingBuffer and dependent {@link Consumer}(s)
     */
    private static final class RingBufferConsumerBarrier<T extends Entry> implements ConsumerBarrier<T>
    {
        private final RingBuffer<T> ringBuffer;
        private final Consumer[] consumers;
        private final WaitStrategy waitStrategy;
        private volatile boolean alerted = false;

        public RingBufferConsumerBarrier(final RingBuffer<T> ringBuffer,
                                         final WaitStrategy waitStrategy,
                                         final Consumer... consumers)
        {
            this.ringBuffer = ringBuffer;
            this.waitStrategy = waitStrategy;
            this.consumers = consumers;
        }

        @Override
        public T getEntry(final long sequence)
        {
            return ringBuffer.getEntry(sequence);
        }

        @Override
        public long waitFor(final long sequence)
            throws AlertException, InterruptedException
        {
            long availableSequence = waitStrategy.waitFor(ringBuffer, this, sequence);

            if (consumers.length != 0)
            {
                while ((availableSequence = getMinimumSequence(consumers)) < sequence)
                {
                    if (alerted)
                    {
                        throw ALERT_EXCEPTION;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public long waitFor(final long sequence, final long timeout, final TimeUnit units)
            throws AlertException, InterruptedException
        {
            long availableSequence = waitStrategy.waitFor(ringBuffer, this, sequence, timeout, units);

            if (consumers.length != 0)
            {
                while ((availableSequence = getMinimumSequence(consumers)) < sequence)
                {
                    if (alerted)
                    {
                        throw ALERT_EXCEPTION;
                    }
                }
            }

            return availableSequence;
        }

        @Override
        public boolean isAlerted()
        {
            return alerted;
        }

        @Override
        public void alert()
        {
            alerted = true;
            waitStrategy.signalAll();
        }

        @Override
        public void clearAlert()
        {
            alerted = false;
        }
    }

    /**
     * ProducerBarrier that uses a thread yielding strategy when trying to claim a {@link Entry} in the {@link RingBuffer}.
     *
     * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
     */
    private static final class RingBufferProducerBarrier<T extends Entry>
        implements ProducerBarrier<T>
    {
        private final RingBuffer<? extends T> ringBuffer;
        private final Consumer[] consumers;
        private final int threshold;

        public RingBufferProducerBarrier(final RingBuffer<? extends T> ringBuffer,
                                         final int bufferReserve,
                                         final Consumer... consumers)
        {
            this.ringBuffer = ringBuffer;
            this.consumers = consumers;
            this.threshold = ringBuffer.getCapacity() - bufferReserve;
        }

        @Override
        public T claimNext()
        {
            while ((ringBuffer.getCursor() - getMinimumSequence(consumers)) >= threshold)
            {
                Thread.yield();
            }

            return ringBuffer.claimNext();
        }

        @Override
        public T claimSequence(final long sequence)
        {
            while ((sequence - getMinimumSequence(consumers)) >= threshold)
            {
                Thread.yield();
            }

            return ringBuffer.claimSequence(sequence);
        }
    }

    /**
     * Callback into {@link RingBuffer} to signal that the producer has populated the {@link Entry} and it is now ready for use.
     */
    interface CommitCallback
    {
        /**
         * Callback to signal {@link Entry} is ready for consumption.
         *
         * @param sequence of the {@link Entry} that is ready for consumption.
         */
        public void commit(long sequence);
    }
}
