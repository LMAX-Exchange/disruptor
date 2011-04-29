package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

import static com.lmax.disruptor.Util.ceilingNextPowerOfTwo;

/**
 * Ring based store of reusable entries containing the data representing an event being exchanged between producers and consumers.
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
     * @param claimStrategyOption threading strategy for producers claiming slots in the ring.
     * @param waitStrategyOption waiting strategy employed by consumers waiting on events.
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
     * {@link ClaimStrategy.Option}.MULTI_THREADED and {@link WaitStrategy.Option}.BLOCKING
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
     * Claim the next entry in sequence for use by a producer.
     *
     * @return the next entry in the sequence.
     */
    public T claimNext()
    {
        long sequence = claimStrategy.getAndIncrement();
        T entry = (T)entries[(int)sequence & ringModMask];
        entry.setSequence(sequence, appendCallback);

        return entry;
    }

    /**
     * Claim a particular entry in sequence when you know only one producer exists.
     *
     * @param sequence to be claimed
     * @return the claimed entry
     */
    public T claimSequence(final long sequence)
    {
        T entry = (T)entries[(int)sequence & ringModMask];
        entry.setSequence(sequence, setCallback);

        return entry;
    }

    /**
     * Create a barrier that gates on the RingBuffer and a list of {@link EventConsumer}s
     *
     * @param eventConsumers this barrier will track
     * @return the barrier gated as required
     */
    public ThresholdBarrier<T> createBarrier(final EventConsumer... eventConsumers)
    {
        return new RingBufferThresholdBarrier(waitStrategy, eventConsumers);
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

    /**
     * Callback to be used when claiming slots in sequence and cursor is catching up with claim
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
     * Callback to be used when claiming slots and the cursor is explicitly set by the producer when you are sure only one
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
     * Barrier handed out for gating consumers of the RingBuffer and dependent {@link EventConsumer}(s)
     */
    private final class RingBufferThresholdBarrier implements ThresholdBarrier
    {
        private final EventConsumer[] eventConsumers;
        private final WaitStrategy waitStrategy;

        public RingBufferThresholdBarrier(final WaitStrategy waitStrategy, final EventConsumer... eventConsumers)
        {
            this.waitStrategy = waitStrategy;
            this.eventConsumers = eventConsumers;
        }

        @Override
        public RingBuffer getRingBuffer()
        {
            return RingBuffer.this;
        }

        @Override
        public long getAvailableSequence()
        {
            long minimum = cursor;
            for (int i = 0, size = eventConsumers.length; i < size; i++)
            {
                long sequence = eventConsumers[i].getSequence();
                minimum = minimum < sequence ? minimum : sequence;
            }

            return minimum;
        }

        @Override
        public long waitFor(final long sequence)
            throws AlertException, InterruptedException
        {
            if (0 != eventConsumers.length)
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
            if (0 != eventConsumers.length)
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
}
