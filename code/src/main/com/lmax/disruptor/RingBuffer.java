package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.Util.ceilingNextPowerOfTwo;

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

    private final Object[] entries;
    private final int ringModMask;

    private final CommitCallback appendCallback = new AppendCommitCallback();
    private final CommitCallback setCallback = new SetCommitCallback();

    private final ClaimStrategy claimStrategy;
    private final WaitStrategy waitStrategy;

    private volatile long cursor = INITIAL_CURSOR_VALUE;

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
        waitStrategy = waitStrategyOption.newInstance(this);

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
     * Create a {@link ThresholdBarrier} that gates on the RingBuffer and a list of {@link EntryConsumer}s
     *
     * @param entryConsumers this barrier will track
     * @return the barrier gated as required
     */
    public ThresholdBarrier<T> createBarrier(final EntryConsumer... entryConsumers)
    {
        return new RingBufferThresholdBarrier<T>(this, waitStrategy, entryConsumers);
    }

    /**
     * Create a {@link Claimer} on this RingBuffer that tracks dependent {@link EntryConsumer}s.
     *
     * The bufferReserve should be at least the number of producing threads.
     *
     * @param bufferReserve size of of the buffer to be reserved.
     * @param entryConsumers to be tracked to prevent wrapping.
     * @return a {@link Claimer} with the above configuration.
     */
    public Claimer<T> createClaimer(final int bufferReserve, final EntryConsumer... entryConsumers)
    {
        return new YieldingClaimer<T>(this, bufferReserve, entryConsumers);
    }

    /**
     * Get the entry for a given sequence from the RingBuffer
     *
     * @param sequence for the entry.
     * @return entry matching the sequence.
     */
    public T getEntry(final long sequence)
    {
        return (T)entries[(int)sequence & ringModMask];
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
        entry.setSequence(sequence, appendCallback);

        return entry;
    }

    private T claimSequence(final long sequence)
    {
        T entry = (T)entries[(int)sequence & ringModMask];
        entry.setSequence(sequence, setCallback);

        return entry;
    }

    /**
     * Callback to be used when claiming {@link Entry}s in sequence and cursor is catching up with claim
     * for notifying the the consumers of progress. This will busy spin on the commit until previous
     * producers have committed lower sequence entries.
     */
    private final class AppendCommitCallback implements CommitCallback
    {
        public void commit(final long sequence)
        {
            final long sequenceMinusOne = sequence - 1;
            while (cursor != sequenceMinusOne)
            {
                // busy spin
            }
            cursor = sequence;

            waitStrategy.notifyConsumers();
        }
    }

    /**
     * Callback to be used when claiming {@link Entry}s and the cursor is explicitly set by the producer when you are sure only one
     * producer exists.
     */
    private final class SetCommitCallback implements CommitCallback
    {
        public void commit(final long sequence)
        {
            claimStrategy.setSequence(sequence + 1);
            cursor = sequence;

            waitStrategy.notifyConsumers();
        }
    }

    /**
     * Barrier handed out for gating consumers of the RingBuffer and dependent {@link EntryConsumer}(s)
     */
    private static final class RingBufferThresholdBarrier<T extends Entry> implements ThresholdBarrier<T>
    {
        private final RingBuffer<? extends T> ringBuffer;
        private final EntryConsumer[] entryConsumers;
        private final WaitStrategy waitStrategy;

        public RingBufferThresholdBarrier(final RingBuffer<? extends T> ringBuffer,
                                          final WaitStrategy waitStrategy,
                                          final EntryConsumer... entryConsumers)
        {
            this.ringBuffer = ringBuffer;
            this.waitStrategy = waitStrategy;
            this.entryConsumers = entryConsumers;
        }

        @Override
        public RingBuffer<? extends T> getRingBuffer()
        {
            return ringBuffer;
        }

        @Override
        public long getAvailableSequence()
        {
            long minimum = ringBuffer.getCursor();
            for (int i = 0, size = entryConsumers.length; i < size; i++)
            {
                long sequence = entryConsumers[i].getSequence();
                minimum = minimum < sequence ? minimum : sequence;
            }

            return minimum;
        }

        @Override
        public long waitFor(final long sequence)
            throws AlertException, InterruptedException
        {
            if (0 != entryConsumers.length)
            {
                long availableSequence = getAvailableSequence();
                if (availableSequence >= sequence)
                {
                    return availableSequence;
                }

                waitStrategy.waitFor(sequence);

                while ((availableSequence = getAvailableSequence()) < sequence)
                {
                    waitStrategy.checkForAlert();
                }

                return availableSequence;
            }

            return waitStrategy.waitFor(sequence);
        }

        @Override
        public long waitFor(final long sequence, final long timeout, final TimeUnit units)
            throws InterruptedException, AlertException
        {
            if (0 != entryConsumers.length)
            {
                long availableSequence = getAvailableSequence();
                if (availableSequence >= sequence)
                {
                    return availableSequence;
                }

                waitStrategy.waitFor(sequence, timeout, units);

                while ((availableSequence = getAvailableSequence()) < sequence)
                {
                    waitStrategy.checkForAlert();
                }

                return availableSequence;
            }

            return waitStrategy.waitFor(sequence, timeout, units);
        }

        @Override
        public void alert()
        {
            waitStrategy.alert();
        }
    }

    /**
     * Claimer that uses a thread yielding strategy when trying to claim a {@link Entry} in the {@link RingBuffer}.
     *
     * @param <T> {@link Entry} implementation stored in the {@link RingBuffer}
     */
    private final class YieldingClaimer<T extends Entry>
        implements Claimer<T>
    {
        private final RingBuffer<? extends T> ringBuffer;
        private final int bufferReserve;
        private final EntryConsumer[] gatingEntryConsumers;

        public YieldingClaimer(final RingBuffer<? extends T> ringBuffer,
                               final int bufferReserve,
                               final EntryConsumer... gatingEntryConsumers)
        {
            this.bufferReserve = bufferReserve;
            this.ringBuffer = ringBuffer;
            this.gatingEntryConsumers = gatingEntryConsumers;
        }

        @Override
        public T claimNext()
        {
            final long threshold = ringBuffer.getCapacity() - getBufferReserve();
            while (ringBuffer.getCursor() - getConsumedEntrySequence() >= threshold)
            {
                Thread.yield();
            }

            return ringBuffer.claimNext();
        }

        @Override
        public T claimSequence(final long sequence)
        {
            final long threshold = ringBuffer.getCapacity() - getBufferReserve();
            while (sequence - getConsumedEntrySequence() >= threshold)
            {
                Thread.yield();
            }

            return ringBuffer.claimSequence(sequence);
        }

        @Override
        public RingBuffer<? extends T> getRingBuffer()
        {
            return ringBuffer;
        }

        @Override
        public long getConsumedEntrySequence()
        {
            long minimum = ringBuffer.getCursor();

            for (EntryConsumer consumer : gatingEntryConsumers)
            {
                long sequence = consumer.getSequence();
                minimum = minimum < sequence ? minimum : sequence;
            }

            return minimum;
        }

        @Override
        public int getBufferReserve()
        {
            return bufferReserve;
        }
    }
}
