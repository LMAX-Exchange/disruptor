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
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RewindBatchEventProcessorTest
{
    private static final int BUFFER_SIZE = 2048;
    private RingBuffer<LongEvent> ringBuffer;
    private int numberOfTimesRewound = 0;
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 0, 1, 0, false, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 0)));
    }

    @Test
    public void shouldRewindOnFirstEventOfBatch()
    {
        int ringBufferEntries = 10;
        int lastSequenceNumber = ringBufferEntries - 1;
        fill(ringBuffer, ringBufferEntries);

        final TestEventHandler eventHandler = new TestEventHandler(values, 0, 1, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 8, 1, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, lastSequenceNumber, 1, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 4, 1, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 0, 1, 0, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 8, 3, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, lastSequenceNumber, 3, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 5, 3, lastSequenceNumber, true, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 0, 3, 0, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, -1, -1, lastSequenceNumber, false, 8);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, -1, -1, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 15, 3, lastSequenceNumber, false, -1);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 15, 3, lastSequenceNumber, false, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        CountingRewindPauseStrategy rewindPauseStrategy = new CountingRewindPauseStrategy();
        eventProcessor.setRewindPauseStrategy(rewindPauseStrategy);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, -1, -1, lastSequenceNumber, false, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        CountingRewindPauseStrategy rewindPauseStrategy = new CountingRewindPauseStrategy();
        eventProcessor.setRewindPauseStrategy(rewindPauseStrategy);
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

        final TestEventHandler eventHandler = new TestEventHandler(values, 15, 3, lastSequenceNumber, false, -1);
        final BatchEventProcessor<LongEvent> eventProcessor = create(eventHandler);
        eventHandler.setRewindable(eventProcessor);

        eventProcessor.setRewindPauseStrategy(new NanosecondRewindPauseStrategy(1000));
        eventProcessor.run();

        assertThat(values, containsExactSequence(
                event(0, 14),
                event(0, 14),
                event(0, 14),
                event(0, lastSequenceNumber)));

    }

    private EventRangeExpectation event(final long sequenceStart, final long sequenceEnd)
    {
        return new EventRangeExpectation(sequenceStart, sequenceEnd, false);
    }

    private BatchEventProcessor<LongEvent> create(final TestEventHandler eventHandler)
    {
        return new BatchEventProcessor<>(
                ringBuffer,
                ringBuffer.newBarrier(),
                eventHandler);
    }

    private final class TestEventHandler implements EventHandler<LongEvent>
    {
        private final List<EventResult> values;
        private int rewindableErrorSequence;
        private BatchEventProcessor<LongEvent> processor;
        private final int numberOfTimesToRewind;
        private final long exitValue;
        private final boolean multipleFails;
        private final int nonRewindableErrorSequence;
        boolean firstPassComplete = false;

        private TestEventHandler(
                final List<EventResult> values,
                final int rewindableErrorSequence,
                final int numberOfTimesToRewind,
                final long exitValue,
                final boolean multipleFails,
                final int nonRewindableErrorSequence)
        {
            this.values = values;
            this.rewindableErrorSequence = rewindableErrorSequence;
            this.numberOfTimesToRewind = numberOfTimesToRewind;
            this.exitValue = exitValue;
            this.multipleFails = multipleFails;
            this.nonRewindableErrorSequence = nonRewindableErrorSequence;
        }

        public void setRewindable(final BatchEventProcessor<LongEvent> processor)
        {
            this.processor = processor;
        }

        public void onEvent(final LongEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            if (multipleFails)
            {
                if ((sequence == rewindableErrorSequence) && (numberOfTimesRewound != numberOfTimesToRewind) && !firstPassComplete)
                {
                    numberOfTimesRewound++;
                    if (numberOfTimesRewound == numberOfTimesToRewind)
                    {
                        firstPassComplete = true;
                        numberOfTimesRewound = 0;
                    }

                    throw new RewindableException(new RuntimeException());
                }

                if ((sequence == rewindableErrorSequence + 2) && (numberOfTimesRewound != numberOfTimesToRewind) && firstPassComplete)
                {
                    numberOfTimesRewound++;
                    throw new RewindableException(new RuntimeException());
                }
            }
            else
            {
                if (sequence == nonRewindableErrorSequence)
                {
                    throw new RuntimeException("not rewindable");
                }

                if ((sequence == rewindableErrorSequence) && (numberOfTimesRewound != numberOfTimesToRewind))
                {
                    numberOfTimesRewound++;
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
        return new TypeSafeMatcher<List<EventResult>>()
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
                for (int i = 0; i < ranges.length; i++)
                {
                    final EventRangeExpectation range = ranges[i];
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

    private static final class CountingRewindPauseStrategy implements RewindPauseStrategy
    {
        int count = 0;
        @Override
        public void pause()
        {
            count++;
        }
    }
}