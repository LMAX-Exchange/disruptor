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

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class SequencerTest
{
    private static final int BUFFER_SIZE = 16;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(DaemonThreadFactory.INSTANCE);


    private static Sequencer newProducer(ProducerType producerType, WaitStrategy waitStrategy)
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

    private final Sequence gatingSequence = new Sequence();

    public static Stream<Arguments> generateData()
    {
        return Stream.of(
                arguments(newProducer(ProducerType.SINGLE, new BlockingWaitStrategy())),
                arguments(newProducer(ProducerType.MULTI, new BlockingWaitStrategy()))
        );
    }

    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldStartWithInitialValue(Sequencer sequencer)
    {
        assertEquals(0, sequencer.next());
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldBatchClaim(Sequencer sequencer)
    {
        assertEquals(3, sequencer.next(4));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldIndicateHasAvailableCapacity(Sequencer sequencer)
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
    @MethodSource("generateData")
    public void shouldIndicateNoAvailableCapacity(Sequencer sequencer)
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        assertFalse(sequencer.hasAvailableCapacity(1));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldHoldUpPublisherWhenBufferIsFull(Sequencer sequencer)
            throws InterruptedException
    {
        sequencer.addGatingSequences(gatingSequence);
        long sequence = sequencer.next(BUFFER_SIZE);
        sequencer.publish(sequence - (BUFFER_SIZE - 1), sequence);

        final CountDownLatch waitingLatch = new CountDownLatch(1);
        final CountDownLatch doneLatch = new CountDownLatch(1);

        final long expectedFullSequence = Sequencer.INITIAL_CURSOR_VALUE + sequencer.getBufferSize();
        assertEquals(expectedFullSequence, sequencer.getCursor());

        executor.submit(
                () ->
                {
                    waitingLatch.countDown();

                    long next = sequencer.next();
                    sequencer.publish(next);

                    doneLatch.countDown();
                });

        waitingLatch.await();
        assertEquals(expectedFullSequence, sequencer.getCursor());

        gatingSequence.set(Sequencer.INITIAL_CURSOR_VALUE + 1L);

        doneLatch.await();
        assertEquals(expectedFullSequence + 1L, sequencer.getCursor());
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldThrowInsufficientCapacityExceptionWhenSequencerIsFull(Sequencer sequencer)
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
    @MethodSource("generateData")
    public void shouldCalculateRemainingCapacity(Sequencer sequencer)
    {
        sequencer.addGatingSequences(gatingSequence);

        assertEquals(BUFFER_SIZE, sequencer.remainingCapacity());
        for (int i = 1; i < BUFFER_SIZE; i++)
        {
            sequencer.next();
            assertEquals(BUFFER_SIZE - i, sequencer.remainingCapacity());
        }
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldNotBeAvailableUntilPublished(Sequencer sequencer)
    {
        long next = sequencer.next(6);

        for (int i = 0; i <= 5; i++)
        {
            assertFalse(sequencer.isAvailable(i));
        }

        sequencer.publish(next - (6 - 1), next);

        for (int i = 0; i <= 5; i++)
        {
            assertTrue(sequencer.isAvailable(i));
        }

        assertFalse(sequencer.isAvailable(6));
    }


    private static Stream<Arguments> producerTypeGenerator()
    {
        return Stream.of(arguments(ProducerType.SINGLE), arguments(ProducerType.MULTI));
    }

    @ParameterizedTest
    @MethodSource("producerTypeGenerator")
    public void shouldNotifyWaitStrategyOnPublish(ProducerType producerType)
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, waitStrategy);

        sequencer.publish(sequencer.next());

        assertEquals(1, waitStrategy.signalAllWhenBlockingCalls);
    }


    @ParameterizedTest
    @MethodSource("producerTypeGenerator")
    public void shouldNotifyWaitStrategyOnPublishBatch(ProducerType producerType)
    {
        final DummyWaitStrategy waitStrategy = new DummyWaitStrategy();
        final Sequenced sequencer = newProducer(producerType, waitStrategy);

        long next = sequencer.next(4);
        sequencer.publish(next - (4 - 1), next);

        assertEquals(1, waitStrategy.signalAllWhenBlockingCalls);
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldWaitOnPublication(Sequencer sequencer) throws Exception
    {
        SequenceBarrier barrier = sequencer.newBarrier();

        long next = sequencer.next(10);
        long lo = next - (10 - 1);
        long mid = next - 5;

        for (long l = lo; l < mid; l++)
        {
            sequencer.publish(l);
        }

        assertEquals(mid - 1, barrier.waitFor(-1));

        for (long l = mid; l <= next; l++)
        {
            sequencer.publish(l);
        }

        assertEquals(next, barrier.waitFor(-1));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldTryNext(Sequencer sequencer) throws Exception
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


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldClaimSpecificSequence(Sequencer sequencer)
    {
        long sequence = 14L;

        sequencer.claim(sequence);
        sequencer.publish(sequence);
        assertEquals(sequence + 1, sequencer.next());
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldNotAllowBulkNextLessThanZero(Sequencer sequencer)
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.next(-1));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldNotAllowBulkNextOfZero(Sequencer sequencer)
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.next(0));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldNotAllowBulkTryNextLessThanZero(Sequencer sequencer)
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.tryNext(-1));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldNotAllowBulkTryNextOfZero(Sequencer sequencer)
    {
        assertThrows(IllegalArgumentException.class, () -> sequencer.tryNext(0));
    }
}
