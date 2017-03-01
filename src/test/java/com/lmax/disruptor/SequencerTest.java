package com.lmax.disruptor;

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.support.DummyWaitStrategy;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SequencerTest
{
    private static final int BUFFER_SIZE = 16;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);

    private final Sequencer sequencer;
    private final Sequence gatingSequence = new Sequence();
    private final ProducerType producerType;

    public SequencerTest(ProducerType producerType, WaitStrategy waitStrategy)
    {
        this.producerType = producerType;
        this.sequencer = newProducer(producerType, BUFFER_SIZE, waitStrategy);
    }

    @Parameters
    public static Collection<Object[]> generateData()
    {
        Object[][] allocators =
            {
                {ProducerType.SINGLE, new BlockingWaitStrategy()},
                {ProducerType.MULTI, new BlockingWaitStrategy()},
            };
        return Arrays.asList(allocators);
    }

    @Test
    public void shouldStartWithInitialValue()
    {
        assertEquals(0, sequencer.next());
    }

    @Test
    public void shouldBatchClaim()
    {
        assertEquals(3, sequencer.next(4));
    }

    @Test
    public void shouldIndicateHasAvailableCapacity()
    {
        sequencer.addGatingSequences(gatingSequence);

        assertTrue(sequencer.hasAvailableCapacity(1));
        assertTrue(sequencer.hasAvailableCapacity(BUFFER_SIZE));
        assertFalse(sequencer.hasAvailableCapacity(BUFFER_SIZE + 1));

        sequencer.publish(sequencer.next());

        assertTrue(sequencer.hasAvailableCapacity(BUFFER_SIZE - 1));
        assertFalse(sequencer.hasAvailableCapacity(BUFFER_SIZE));
    }

    @Test
    public void shouldIndicateNoAvailableCapacity()
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        assertFalse(sequencer.hasAvailableCapacity(1));
    }

    @Test
    public void shouldHoldUpPublisherWhenBufferIsFull()
        throws InterruptedException
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        assertThat(sequencer.getCursor(), is(expectedFullSequence));

        executor.submit(
            new Runnable()
            {
                @Override
                public void run()
                {
                    waitingLatch.countDown();

                    long next = sequencer.next();
                    sequencer.publish(next);

                    doneLatch.countDown();
                }
            });

        waitingLatch.await();
        assertThat(sequencer.getCursor(), is(expectedFullSequence));

        gatingSequence.set(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertThat(sequencer.getCursor(), is(expectedFullSequence + 1L));
    }

    @Test(expected = InsufficientCapacityException.class)
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull() throws Exception
    {
        sequencer.addGatingSequences(gatingSequence);
        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            sequencer.next();
        }
        sequencer.tryNext();
    }

    @Test
    public void shouldCalculateRemainingCapacity() throws Exception
    {
        sequencer.addGatingSequences(gatingSequence);

        assertThat(sequencer.remainingCapacity(), is((long) BUFFER_SIZE));
        for (int i = 1; i < BUFFER_SIZE; i++)
        {
            sequencer.next();
            assertThat(sequencer.remainingCapacity(), is((long) BUFFER_SIZE - i));
        }
    }

    @Test
    public void shouldNotBeAvailableUntilPublished() throws Exception
    {
        long next = sequencer.next(6);

        for (int i = 0; i <= 5; i++)
        {
            assertThat(sequencer.isAvailable(i), is(false));
        }

        sequencer.publish(next - (6 - 1), next);

        for (int i = 0; i <= 5; i++)
        {
            assertThat(sequencer.isAvailable(i), is(true));
        }

        assertThat(sequencer.isAvailable(6), is(false));
    }

    @Test
    public void shouldNotifyWaitStrategyOnPublish() throws Exception
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, BUFFER_SIZE, waitStrategy);

        sequencer.publish(sequencer.next());

        assertThat(waitStrategy.signalAllWhenBlockingCalls, is(1));
    }

    @Test
    public void shouldNotifyWaitStrategyOnPublishBatch() throws Exception
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, BUFFER_SIZE, waitStrategy);

        long next = sequencer.next(4);
        sequencer.publish(next - (4 - 1), next);

        assertThat(waitStrategy.signalAllWhenBlockingCalls, is(1));
    }

    @Test
    public void shouldWaitOnPublication() throws Exception
    {
        SequenceBarrier barrier = sequencer.newBarrier();

        long next = sequencer.next(10);
        long lo = next - (10 - 1);
        long mid = next - 5;

        for (long l = lo; l < mid; l++)
        {
            sequencer.publish(l);
        }

        assertThat(barrier.waitFor(-1), is(mid - 1));

        for (long l = mid; l <= next; l++)
        {
            sequencer.publish(l);
        }

        assertThat(barrier.waitFor(-1), is(next));
    }

    @Test
    public void shouldTryNext() throws Exception
    {
        sequencer.addGatingSequences(gatingSequence);

        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            sequencer.publish(sequencer.tryNext());
        }

        try
        {
            sequencer.tryNext();
            fail("Should of thrown: " + InsufficientCapacityException.class.getSimpleName());
        }
        catch (InsufficientCapacityException e)
        {
            // No-op
        }
    }

    @Test
    public void shouldClaimSpecificSequence() throws Exception
    {
        long sequence = 14L;

        sequencer.claim(sequence);
        sequencer.publish(sequence);
        assertThat(sequencer.next(), is(sequence + 1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowBulkNextLessThanZero() throws Exception
    {
        sequencer.next(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowBulkNextOfZero() throws Exception
    {
        sequencer.next(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowBulkTryNextLessThanZero() throws Exception
    {
        sequencer.tryNext(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowBulkTryNextOfZero() throws Exception
    {
        sequencer.tryNext(0);
    }

    private Sequencer newProducer(ProducerType producerType, int bufferSize, WaitStrategy waitStrategy)
    {
        switch (producerType)
        {
            case SINGLE:
                return new SingleProducerSequencer(bufferSize, waitStrategy);
            case MULTI:
                return new MultiProducerSequencer(bufferSize, waitStrategy);
            default:
                throw new IllegalStateException(producerType.toString());
        }
    }
}
