package com.lmax.disruptor;

import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.support.DummyWaitStrategy;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class SequencerTest
{
    private static final int BUFFER_SIZE = 16;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);
    private final Sequence gatingSequence = new Sequence();

    private static Stream<Arguments> sequencerGenerator()
    {
        return Stream.of(
                arguments(newProducer(ProducerType.SINGLE, new BlockingWaitStrategy())),
                arguments(newProducer(ProducerType.MULTI, new BlockingWaitStrategy()))
        );
    }

    private static Stream<Arguments> producerTypeGenerator()
    {
        return Stream.of(arguments(ProducerType.SINGLE), arguments(ProducerType.MULTI));
    }

    private static Sequencer newProducer(final ProducerType producerType, final WaitStrategy waitStrategy)
    {
        switch (producerType)
        {
            case SINGLE:
                return new SingleProducerSequencer(BUFFER_SIZE, waitStrategy);
            case MULTI:
                return new MultiProducerSequencer(BUFFER_SIZE, waitStrategy);
            default:
                throw new IllegalStateException(producerType.toString());
        }
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldStartWithInitialValue(final Sequencer sequencer)
    {
        assertEquals(0, sequencer.next());
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldBatchClaim(final Sequencer sequencer)
    {
        assertEquals(3, sequencer.next(4));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldIndicateHasAvailableCapacity(final Sequencer sequencer)
    {
        sequencer.addGatingSequences(gatingSequence);

        assertTrue(sequencer.hasAvailableCapacity(1));
        assertTrue(sequencer.hasAvailableCapacity(BUFFER_SIZE));
        assertFalse(sequencer.hasAvailableCapacity(BUFFER_SIZE + 1));

        sequencer.publish(sequencer.next());

        assertTrue(sequencer.hasAvailableCapacity(BUFFER_SIZE - 1));
        assertFalse(sequencer.hasAvailableCapacity(BUFFER_SIZE));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldIndicateNoAvailableCapacity(final Sequencer sequencer)
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        assertFalse(sequencer.hasAvailableCapacity(1));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldHoldUpPublisherWhenBufferIsFull(final Sequencer sequencer)
        throws InterruptedException
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        assertThat(
            sequencer.getHighestPublishedSequence(Sequencer.INITIAL_CURSOR_VALUE + 1, sequencer.getCursor()),
            is(expectedFullSequence));

        executor.submit(
                () ->
                {
                    waitingLatch.countDown();

                    long next = sequencer.next();
                    sequencer.publish(next);

                    doneLatch.countDown();
                });

        waitingLatch.await();
        assertThat(
            sequencer.getHighestPublishedSequence(expectedFullSequence, sequencer.getCursor()),
            is(expectedFullSequence));

        gatingSequence.set(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertThat(sequencer.getHighestPublishedSequence(expectedFullSequence, sequencer.getCursor()), is(expectedFullSequence + 1L));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull(final Sequencer sequencer) throws Exception
    {
        assertThrows(InsufficientCapacityException.class, () ->
        {
            sequencer.addGatingSequences(gatingSequence);
            for (int i = 0; i < BUFFER_SIZE; i++)
            {
                sequencer.next();
            }
            sequencer.tryNext();
        });
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldCalculateRemainingCapacity(final Sequencer sequencer) throws Exception
    {
        sequencer.addGatingSequences(gatingSequence);

        assertThat(sequencer.remainingCapacity(), is((long) BUFFER_SIZE));
        for (int i = 1; i < BUFFER_SIZE; i++)
        {
            sequencer.next();
            assertThat(sequencer.remainingCapacity(), is((long) BUFFER_SIZE - i));
        }
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldNotBeAvailableUntilPublished(final Sequencer sequencer) throws Exception
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

    @ParameterizedTest
    @MethodSource("producerTypeGenerator")
    public void shouldNotifyWaitStrategyOnPublish(final ProducerType producerType) throws Exception
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, waitStrategy);

        sequencer.publish(sequencer.next());

        assertThat(waitStrategy.signalAllWhenBlockingCalls, is(1));
    }

    @ParameterizedTest
    @MethodSource("producerTypeGenerator")
    public void shouldNotifyWaitStrategyOnPublishBatch(final ProducerType producerType) throws Exception
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, waitStrategy);

        long next = sequencer.next(4);
        sequencer.publish(next - (4 - 1), next);

        assertThat(waitStrategy.signalAllWhenBlockingCalls, is(1));
    }


    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldWaitOnPublication(final Sequencer sequencer) throws Exception
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

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldTryNext(final Sequencer sequencer) throws Exception
    {
        sequencer.addGatingSequences(gatingSequence);

        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            sequencer.publish(sequencer.tryNext());
        }

        assertThrows(InsufficientCapacityException.class, sequencer::tryNext);
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldClaimSpecificSequence(final Sequencer sequencer) throws Exception
    {
        long sequence = 14L;

        sequencer.claim(sequence);
        sequencer.publish(sequence);
        assertThat(sequencer.next(), is(sequence + 1));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldNotAllowBulkNextLessThanZero(final Sequencer sequencer) throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.next(-1));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldNotAllowBulkNextOfZero(final Sequencer sequencer) throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.next(0));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldNotAllowBulkTryNextLessThanZero(final Sequencer sequencer) throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.tryNext(-1));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    public void shouldNotAllowBulkTryNextOfZero(final Sequencer sequencer) throws Exception
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.tryNext(0));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    void sequencesBecomeAvailableAfterAPublish(final Sequencer sequencer)
    {
        final long seq = sequencer.next();
        assertFalse(sequencer.isAvailable(seq));
        sequencer.publish(seq);

        assertTrue(sequencer.isAvailable(seq));
    }

    @ParameterizedTest
    @MethodSource("sequencerGenerator")
    void sequencesBecomeUnavailableAfterWrapping(final Sequencer sequencer)
    {
        final long seq = sequencer.next();
        sequencer.publish(seq);
        assertTrue(sequencer.isAvailable(seq));

        for (int i = 0; i < BUFFER_SIZE; i++)
        {
            sequencer.publish(sequencer.next());
        }

        assertFalse(sequencer.isAvailable(seq));
    }
}
