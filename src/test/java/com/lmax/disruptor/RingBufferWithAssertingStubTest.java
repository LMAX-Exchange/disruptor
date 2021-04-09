package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class RingBufferWithAssertingStubTest
{
    private RingBuffer<StubEvent> ringBuffer;
    private Sequencer sequencer;

    @BeforeEach
    public void setUp()
    {
        sequencer = new AssertingSequencer(16);

        ringBuffer = new RingBuffer<>(StubEvent.EVENT_FACTORY, sequencer);
    }

    @Test
    public void shouldDelegateNextAndPublish()
    {
        ringBuffer.publish(ringBuffer.next());
    }

    @Test
    public void shouldDelegateTryNextAndPublish() throws Exception
    {
        ringBuffer.publish(ringBuffer.tryNext());
    }

    @Test
    public void shouldDelegateNextNAndPublish() throws Exception
    {
        long hi = ringBuffer.next(10);
        ringBuffer.publish(hi - 9, hi);
    }

    @Test
    public void shouldDelegateTryNextNAndPublish() throws Exception
    {
        long hi = ringBuffer.tryNext(10);
        ringBuffer.publish(hi - 9, hi);
    }

    private static final class AssertingSequencer implements Sequencer
    {
        private final int size;
        private long lastBatchSize = -1;
        private long lastValue = -1;

        private AssertingSequencer(final int size)
        {
            this.size = size;
        }

        @Override
        public int getBufferSize()
        {
            return size;
        }

        @Override
        public boolean hasAvailableCapacity(final int requiredCapacity)
        {
            return requiredCapacity <= size;
        }

        @Override
        public long remainingCapacity()
        {
            return size;
        }

        @Override
        public long next()
        {
            lastValue = ThreadLocalRandom.current().nextLong(0, 1000000);
            lastBatchSize = 1;
            return lastValue;
        }

        @Override
        public long next(final int n)
        {
            lastValue = ThreadLocalRandom.current().nextLong(n, 1000000);
            lastBatchSize = n;
            return lastValue;
        }

        @Override
        public long tryNext() throws InsufficientCapacityException
        {
            return next();
        }

        @Override
        public long tryNext(final int n) throws InsufficientCapacityException
        {
            return next(n);
        }

        @Override
        public void publish(final long sequence)
        {
            assertThat(sequence, is(lastValue));
            assertThat(lastBatchSize, is(1L));
        }

        @Override
        public void publish(final long lo, final long hi)
        {
            assertThat(hi, is(lastValue));
            assertThat((hi - lo) + 1, is(lastBatchSize));
        }

        @Override
        public long getCursor()
        {
            return lastValue;
        }

        @Override
        public void claim(final long sequence)
        {

        }

        @Override
        public boolean isAvailable(final long sequence)
        {
            return false;
        }

        @Override
        public void addGatingSequences(final Sequence... gatingSequences)
        {

        }

        @Override
        public boolean removeGatingSequence(final Sequence sequence)
        {
            return false;
        }

        @Override
        public SequenceBarrier newBarrier(final Sequence... sequencesToTrack)
        {
            return null;
        }

        @Override
        public long getMinimumSequence()
        {
            return 0;
        }

        @Override
        public long getHighestPublishedSequence(final long nextSequence, final long availableSequence)
        {
            return 0;
        }

        @Override
        public <T> EventPoller<T> newPoller(final DataProvider<T> provider, final Sequence... gatingSequences)
        {
            return null;
        }
    }
}
