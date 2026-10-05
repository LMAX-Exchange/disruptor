package com.lmax.disruptor;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.II_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * Two producers and a single consumer sharing one {@link MultiProducerSequencer}.
 *
 * <p>Checks two safety properties:
 * <ol>
 *     <li>the consumer never reads a slot that has not been published (or whose data is not yet visible)</li>
 *     <li>a producer never claims a slot that the consumer has not finished reading</li>
 * </ol>
 *
 * <p>Actors should not block on each other, so most tests have producers use {@link Sequencer#tryNext()} rather
 * than {@link Sequencer#next()}, and the consumer polls a bounded number of times instead of waiting.
 * {@link BlockingNextWrapping} covers {@link Sequencer#next()} with a consumer that reads every claim.
 * Event data is written with plain stores so that any missing happens-before edge shows up as a stale read.
 *
 * <p>Results are {@code (unpublishedReads, overwrites)}; anything other than {@code "0, 0"} is a bug.
 *
 * <p>To run (from the repo root):
 * <pre>
 * # All jcstress tests in the repo; mode is one of sanity, quick (default), default, tough, stress
 * ./gradlew jcstress -Pmode=quick
 *
 * # Only these tests: build the jar, then run jcstress directly, filtering by name with -t
 * ./gradlew jcstressJar
 * CP=$(find ~/.gradle -name 'jcstress-core-0.11.jar' -o -name 'jopt-simple*.jar' | tr '\n' ':')
 * java -cp build/libs/disruptor-*-jcstress.jar:$CP org.openjdk.jcstress.Main -t TwoProducersOneConsumer -m quick
 * </pre>
 * Narrow {@code -t} to a single variant (e.g. {@code -t BlockingNextWrapping}) and add {@code -v} to print every
 * outcome. jcstress writes {@code jcstress-results-*.bin.gz} to the working directory.
 * {@link BlockingNextWrapping} reports a lost slot as a hang (timeout) rather than a FORBIDDEN outcome.
 */
public final class MultiProducerSequencerTwoProducersOneConsumerStress
{
    private static final int CLAIMS_PER_PRODUCER = 2;
    private static final int TOTAL_CLAIMS = CLAIMS_PER_PRODUCER * 2;
    private static final int CONSUMER_POLLS = 4;

    static final class Harness
    {
        private final int bufferSize;
        private final int mask;
        private final MultiProducerSequencer sequencer;
        private final Sequence consumed = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
        private final long[] data;
        private final boolean[] read = new boolean[TOTAL_CLAIMS];

        private int unpublishedReads;
        private int overwrites1;
        private int overwrites2;

        Harness(final int bufferSize)
        {
            this.bufferSize = bufferSize;
            this.mask = bufferSize - 1;
            this.sequencer = new MultiProducerSequencer(bufferSize, new BlockingWaitStrategy());
            this.sequencer.addGatingSequences(consumed);
            this.data = new long[bufferSize];
        }

        int produce()
        {
            int overwrites = 0;
            for (int i = 0; i < CLAIMS_PER_PRODUCER; i++)
            {
                final long sequence;
                try
                {
                    sequence = sequencer.tryNext();
                }
                catch (final InsufficientCapacityException e)
                {
                    continue;
                }

                if (sequence >= bufferSize && !read[(int) (sequence - bufferSize)])
                {
                    overwrites++;
                }

                data[(int) sequence & mask] = sequence + 1;
                sequencer.publish(sequence);
            }
            return overwrites;
        }

        int produceBlocking()
        {
            int overwrites = 0;
            for (int i = 0; i < CLAIMS_PER_PRODUCER; i++)
            {
                final long sequence = sequencer.next();

                if (sequence >= bufferSize && !read[(int) (sequence - bufferSize)])
                {
                    overwrites++;
                }

                data[(int) sequence & mask] = sequence + 1;
                sequencer.publish(sequence);
            }
            return overwrites;
        }

        void producer1()
        {
            overwrites1 = produce();
        }

        void producer2()
        {
            overwrites2 = produce();
        }

        void blockingProducer1()
        {
            overwrites1 = produceBlocking();
        }

        void blockingProducer2()
        {
            overwrites2 = produceBlocking();
        }

        void consumer()
        {
            for (int i = 0; i < CONSUMER_POLLS; i++)
            {
                poll();
            }
        }

        void consumeAll()
        {
            while (consumed.get() < TOTAL_CLAIMS - 1)
            {
                if (!poll())
                {
                    Thread.onSpinWait();
                }
            }
        }

        private boolean poll()
        {
            final long next = consumed.get() + 1;
            final long available = sequencer.getHighestPublishedSequence(next, sequencer.getCursor());

            for (long sequence = next; sequence <= available; sequence++)
            {
                if (data[(int) sequence & mask] != sequence + 1)
                {
                    unpublishedReads++;
                }
                read[(int) sequence] = true;
            }

            if (available >= next)
            {
                consumed.set(available);
                return     true;
            }
            return false;
        }

        void arbiter(final II_Result r)
        {
            r.r1 = unpublishedReads;
            r.r2 = overwrites1 + overwrites2;
        }
    }

    /**
     * Buffer large enough for every claim, so no wrapping occurs. Exercises out-of-order publication by the two
     * producers: the consumer must stop at the first gap and never read an unpublished slot.
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Only published slots read, no unread slot overwritten.")
    @Outcome(expect = FORBIDDEN, desc = "Read an unpublished slot or overwrote an unread slot.")
    @State
    public static class NoWrap
    {
        final Harness harness = new Harness(4);

        @Actor
        public void producer1()
        {
            harness.producer1();
        }

        @Actor
        public void producer2()
        {
            harness.producer2();
        }

        @Actor
        public void consumer()
        {
            harness.consumer();
        }

        @Arbiter
        public void arbiter(final II_Result r)
        {
            harness.arbiter(r);
        }
    }

    /**
     * Buffer smaller than the total number of claims, forcing producers to wrap. Producers must only reuse a slot
     * once the consumer's gating sequence has moved past it.
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Only published slots read, no unread slot overwritten.")
    @Outcome(expect = FORBIDDEN, desc = "Read an unpublished slot or overwrote an unread slot.")
    @State
    public static class Wrapping
    {
        final Harness harness = new Harness(2);

        @Actor
        public void producer1()
        {
            harness.producer1();
        }

        @Actor
        public void producer2()
        {
            harness.producer2();
        }

        @Actor
        public void consumer()
        {
            harness.consumer();
        }

        @Arbiter
        public void arbiter(final II_Result r)
        {
            harness.arbiter(r);
        }
    }

    /**
     * As {@link Wrapping}, but producers claim with the blocking {@link Sequencer#next()}, exercising its
     * {@code getAndAdd} claim and wrap-point spin. The consumer reads until every claim has been consumed, so
     * producers always wrap and always make progress.
     *
     * <p>Sequences are claimed in order and a producer only waits for the consumer to free an older slot, so this
     * cannot deadlock on a correct sequencer. A lost publish or slot would show up as a hang (jcstress timeout)
     * rather than a FORBIDDEN outcome.
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Only published slots read, no unread slot overwritten.")
    @Outcome(expect = FORBIDDEN, desc = "Read an unpublished slot or overwrote an unread slot.")
    @State
    public static class BlockingNextWrapping
    {
        final Harness harness = new Harness(2);

        @Actor
        public void producer1()
        {
            harness.blockingProducer1();
        }

        @Actor
        public void producer2()
        {
            harness.blockingProducer2();
        }

        @Actor
        public void consumer()
        {
            harness.consumeAll();
        }

        @Arbiter
        public void arbiter(final II_Result r)
        {
            harness.arbiter(r);
        }
    }
}
