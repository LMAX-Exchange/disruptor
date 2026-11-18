package com.lmax.disruptor;

import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.JJ_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

/**
 * A gating sequence added while the producer is running must hold the producer back: the producer may not claim
 * more than a buffer's worth past it.
 *
 * <p>Results are {@code claimed, gate}: the sequence the producer claimed last (or {@code -100} when it was
 * refused) and the value the new gating sequence was given.</p>
 */
public final class SingleProducerSequencerStress
{
    private static final long REFUSED = -100L;

    private static long tryClaim(final Sequencer sequencer)
    {
        try
        {
            return sequencer.tryNext();
        }
        catch (final InsufficientCapacityException e)
        {
            return REFUSED;
        }
    }

    /**
     * The producer publishes 0 and claims 1 on a buffer of one. Claiming 1 is only allowed if the new gating
     * sequence saw 0 published.
     */
    @JCStressTest
    @Outcome(id = "1, 0", expect = ACCEPTABLE, desc = "Gating sequence starts at the published 0, 1 fits.")
    @Outcome(id = {"-100, -1", "-100, 0"}, expect = ACCEPTABLE, desc = "Producer saw the gating sequence at -1.")
    @Outcome(id = "1, -1", expect = FORBIDDEN, desc = "Producer wrapped past a gating sequence that missed 0.")
    @State
    public static class AddGatingSequenceWhilePublishing
    {
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(1, new BusySpinWaitStrategy());
        final Sequence gatingSequence = new Sequence();

        @Actor
        public void producer(final JJ_Result r)
        {
            sequencer.publish(sequencer.next());
            r.r1 = tryClaim(sequencer);
        }

        @Actor
        public void adder(final JJ_Result r)
        {
            sequencer.addGatingSequences(gatingSequence);
            r.r2 = gatingSequence.get();
        }
    }

    /**
     * As {@link AddGatingSequenceWhilePublishing}, but the producer calls hasAvailableCapacity first, and the claim
     * uses the gating sequence it cached.
     */
    @JCStressTest
    @Outcome(id = "1, 0", expect = ACCEPTABLE, desc = "Gating sequence starts at the published 0, 1 fits.")
    @Outcome(id = {"-100, -1", "-100, 0"}, expect = ACCEPTABLE, desc = "Producer saw the gating sequence at -1.")
    @Outcome(id = "1, -1", expect = FORBIDDEN, desc = "Producer wrapped past a gating sequence that missed 0.")
    @State
    public static class AddGatingSequenceAfterHasAvailableCapacity
    {
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(1, new BusySpinWaitStrategy());
        final Sequence gatingSequence = new Sequence();

        @Actor
        public void producer(final JJ_Result r)
        {
            sequencer.publish(sequencer.next());
            sequencer.hasAvailableCapacity(1);
            r.r1 = tryClaim(sequencer);
        }

        @Actor
        public void adder(final JJ_Result r)
        {
            sequencer.addGatingSequences(gatingSequence);
            r.r2 = gatingSequence.get();
        }
    }
}
