package com.lmax.disruptor;

import com.lmax.disruptor.dsl.stubs.StubExceptionHandler;
import com.lmax.disruptor.support.LongEvent;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class RewindBatchEventProcessorTest
{
    private static final int BUFFER_SIZE = 2048;
    private RingBuffer<LongEvent> ringBuffer;
    private final List<EventResult> values = new ArrayList<>();

    @BeforeEach
    public void setUp()
    {
        ringBuffer = RingBuffer.createMultiProducer(LongEvent.FACTORY, BUFFER_SIZE);
    }

    @Test
    public void shouldRewindOnFirstEventOfBatchSizeOfOne()
    {
        fill(ringBuffer, 1);

        final TestEventHandler eventHandler = new TestEventHandler(values, List.of(rewind(0, 1)), 0, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(event(0, 0)));
    }


    @Test
    public void shouldRewindOnFirstEventOfBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(
                values,
                singletonList(rewind(0, 1)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRewindOnEventInMiddleOfBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(
                values,
                singletonList(rewind(8, 1)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 7),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRewindOnLastEventOfBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(
                values,
                singletonList(rewind(lastSequenceNumber, 1)),
                lastSequenceNumber,
                -1
        );

        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 8),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRunBatchCompleteOnLastEventOfBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(4, 1)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 3),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRunBatchCompleteOnLastEventOfBatchOfOne()
    {
        fill(ringBuffer, 1);

        final TestEventHandler eventHandler = new TestEventHandler(values, singletonList(rewind(0, 1)), 0, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 0)));
    }

    @Test
    public void shouldRewindMultipleTimes()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(8, 3)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 7),
                event(0, 7),
                event(0, 7),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRewindMultipleTimesOnLastEventInBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(lastSequenceNumber, 3)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 8),
                event(0, 8),
                event(0, 8),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRewindMultipleTimesInSameBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                asList(rewind(5, 3), rewind(7, 3)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 4),
                event(0, 4),
                event(0, 4),
                event(0, 6),
                event(0, 6),
                event(0, 6),
                event(0, lastSequenceNumber)));
    }

    @Test
    public void shouldRewindMultipleTimesOnBatchOfOne()
    {
        fill(ringBuffer, 1);

        final TestEventHandler eventHandler = new TestEventHandler(values, singletonList(rewind(0, 3)), 0, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        // nothing is actually written as first event is rewound
        assertThat(values, containsExactSequence(
                event(0, 0)));
    }

    @Test
    public void shouldFallOverWhenNonRewindableExceptionIsThrown()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values, emptyList(), lastSequenceNumber, 8);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        AtomicReference<Throwable> exceptionHandled = new AtomicReference<>();
        eventProcessor.setExceptionHandler(new StubExceptionHandler(exceptionHandled));
        eventProcessor.run();
        assertEquals("not rewindable", exceptionHandled.get().getMessage());
    }

    @Test
    public void shouldProcessUpToMaxBatchSizeForEachGivenBatch()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values, emptyList(), lastSequenceNumber, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 9),
                event(10, 19),
                event(20, lastSequenceNumber)));
    }

    @Test
    public void shouldOnlyRewindBatch()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(15, 3)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 14),
                event(0, 14),
                event(0, 14),
                event(0, lastSequenceNumber)));
    }

    @Test
    void shouldInvokeRewindPauseStrategyOnRewind()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(15, 3)),
                lastSequenceNumber,
                -1);
        CountingBatchRewindStrategy rewindPauseStrategy = new CountingBatchRewindStrategy();
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler, rewindPauseStrategy);

        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 14),
                event(0, 14),
                event(0, 14),
                event(0, lastSequenceNumber)));

        assertEquals(3, rewindPauseStrategy.count);
    }

    @Test
    void shouldNotInvokeRewindPauseStrategyWhenNoRewindsOccur()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(-1, -1)),
                lastSequenceNumber,
                -1);
        CountingBatchRewindStrategy rewindPauseStrategy = new CountingBatchRewindStrategy();
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler, rewindPauseStrategy);

        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, lastSequenceNumber)));

        assertEquals(0, rewindPauseStrategy.count);
    }

    @Test
    void shouldCopeWithTheNanosecondRewindPauseStrategy()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                singletonList(rewind(15, 3)),
                lastSequenceNumber,
                -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler, new NanosecondPauseBatchRewindStrategy(1000));

        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 14),
                event(0, 14),
                event(0, 14),
                event(0, lastSequenceNumber)));

    }


    @Test
    void shouldGiveUpWhenUsingTheGiveUpRewindStrategy()
    {
        int ringBufferEntries = 30;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values,
                asList(rewind(15, 99), rewind(25, 99)),
                lastSequenceNumber,
                -1);
        EventuallyGiveUpBatchRewindStrategy batchRewindStrategy = new EventuallyGiveUpBatchRewindStrategy(3);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler, batchRewindStrategy);

        eventHandler.setRewindable(eventProcessor);

        AtomicReference<Throwable> exceptionHandled = new AtomicReference<>();
        eventProcessor.setExceptionHandler(new StubExceptionHandler(exceptionHandled));

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 14),
                event(0, 14),
                event(0, 14),
                event(16, 24), // unable to process 15 so it ends up skipping it
                event(16, 24),
                event(16, 24),
                event(26, lastSequenceNumber))); // unable to process 25 so it ends up skipping it
    }

    @Test
    void shouldNotAllowNullBatchRewindStrategy()
    {
        final TestEventHandler eventHandler = new TestEventHandler(values,
                asList(rewind(15, 99), rewind(25, 99)),
                -1,
                -1);
        final BatchEventProcessorBuilder batchEventProcessorBuilder = new BatchEventProcessorBuilder();
        assertThrows(NullPointerException.class, () -> batchEventProcessorBuilder.build(ringBuffer, ringBuffer.newBarrier(), eventHandler, null));
    }

    private static ForceRewindSequence rewind(final long sequenceNumberToFailOn, final long timesToFail)
    {
        return new ForceRewindSequence(sequenceNumberToFailOn, timesToFail);
    }

    private EventRangeExpectation event(final long sequenceStart, final long sequenceEnd)
    {
        return new EventRangeExpectation(sequenceStart, sequenceEnd, false);
    }

    private BatchEventProcessor<LongEvent> create(final TestEventHandler eventHandler)
    {
        return create(eventHandler, new SimpleBatchRewindStrategy());
    }

    private BatchEventProcessor<LongEvent> create(final TestEventHandler eventHandler, final BatchRewindStrategy batchRewindStrategy)
    {
        return new BatchEventProcessorBuilder().build(
                ringBuffer,
                ringBuffer.newBarrier(),
                eventHandler,
                batchRewindStrategy);
    }

    private static final class TestEventHandler implements RewindableEventHandler<LongEvent>
    {
        private final List<EventResult> values;
        private BatchEventProcessor<LongEvent> processor;
        private final List<ForceRewindSequence> forceRewindSequences;
        private final long exitValue;
        private final int nonRewindableErrorSequence;

        private TestEventHandler(
                final List<EventResult> values,
                final List<ForceRewindSequence> forceRewindSequences,
                final long exitValue,
                final int nonRewindableErrorSequence)
        {
            this.values = values;
            this.forceRewindSequences = forceRewindSequences;
            this.exitValue = exitValue;
            this.nonRewindableErrorSequence = nonRewindableErrorSequence;
        }


        public void setRewindable(final BatchEventProcessor<LongEvent> processor)
        {
            this.processor = processor;
        }

        @Override
        public void onEvent(final LongEvent event, final long sequence, final boolean endOfBatch) throws RewindableException
        {

            if (sequence == nonRewindableErrorSequence)
            {
                throw new RuntimeException("not rewindable");
            }

            Optional<ForceRewindSequence> maybeForceRewindSequence = this.forceRewindSequences.stream()
                    .filter(r -> r.sequenceNumberToFailOn == sequence).findFirst();

            if (maybeForceRewindSequence.isPresent())
            {
                ForceRewindSequence forceRewindSequence = maybeForceRewindSequence.get();

                if (forceRewindSequence.numberOfTimesRewound != forceRewindSequence.timesToFail)
                {
                    forceRewindSequence.numberOfTimesRewound++;
                    throw new RewindableException(new RuntimeException());

                }
            }

            values.add(new EventResult(event.get(), false));

            if (sequence == exitValue)
            {
                processor.halt();
            }
        }
    }

    private static void fill(final RingBuffer<LongEvent> ringBuffer, final int batchSize)
    {
        for (long l = 0; l < batchSize; l++)
        {
            final long next = ringBuffer.next();
            ringBuffer.get(next).set(l);
            ringBuffer.publish(next);
        }
    }

    private static Matcher<List<EventResult>> containsExactSequence(final EventRangeExpectation... ranges)
    {
        return new TypeSafeMatcher<>()
        {
            @Override
            public void describeTo(final Description description)
            {
                description.appendValue(Arrays.toString(ranges));
            }

            @Override
            public boolean matchesSafely(final List<EventResult> item)
            {
                int index = 0;
                for (final EventRangeExpectation range : ranges)
                {
                    for (long v = range.sequenceStart, end = range.sequenceEnd; v <= end; v++)
                    {
                        final EventResult eventResult = item.get(index++);
                        if (eventResult.sequence != v && eventResult.batchFinish != range.batchFinish)
                        {
                            return false;
                        }
                    }
                }

                return item.size() == index;
            }
        };
    }

    private static final class EventRangeExpectation
    {
        private final long sequenceStart;
        private final long sequenceEnd;
        private final boolean batchFinish;

        EventRangeExpectation(final long sequenceStart, final long sequenceEnd, final boolean batchFinish)
        {
            this.sequenceStart = sequenceStart;
            this.sequenceEnd = sequenceEnd;
            this.batchFinish = batchFinish;
        }

        @Override
        public String toString()
        {
            return "{" +
                    sequenceStart +
                    "," + sequenceEnd +
                    "," + batchFinish +
                    '}';
        }
    }


    private static final class EventResult
    {
        final long sequence;
        final boolean batchFinish;

        private EventResult(final long sequence, final boolean batchFinish)
        {
            this.sequence = sequence;
            this.batchFinish = batchFinish;
        }

        @Override
        public String toString()
        {
            return "{" + sequence +
                    "," + batchFinish +
                    '}';
        }
    }

    private static final class CountingBatchRewindStrategy implements BatchRewindStrategy
    {
        int count = 0;

        @Override
        public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
        {
            count++;
            return RewindAction.REWIND;
        }

    }

    private static final class ForceRewindSequence
    {
        final long sequenceNumberToFailOn;
        final long timesToFail;
        long numberOfTimesRewound = 0;

        private ForceRewindSequence(final long sequenceNumberToFailOn, final long timesToFail)
        {
            this.sequenceNumberToFailOn = sequenceNumberToFailOn;
            this.timesToFail = timesToFail;
        }
    }
}