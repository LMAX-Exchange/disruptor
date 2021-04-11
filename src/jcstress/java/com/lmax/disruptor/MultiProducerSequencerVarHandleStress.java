package com.lmax.disruptor;

import com.lmax.disruptor.alternatives.MultiProducerSequencerVarHandle;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.ZZ_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public final class MultiProducerSequencerVarHandleStress
{
    private static Sequencer createSequencer()
    {
        return new MultiProducerSequencerVarHandle(64, new BlockingWaitStrategy());
    }

    @JCStressTest
    @Outcome(id = {"false, false", "true, false", "true, true"}, expect = ACCEPTABLE, desc = "Assuming ordered updates")
    @Outcome(id = "false, true", expect = FORBIDDEN, desc = "publish(2) should not be available before publish(1)")
    @State
    public static class PublishUpdatesIsAvailableLazily
    {
        Sequencer sequencer = createSequencer();

        @Actor
        public void actor1()
        {
            sequencer.publish(1);
            sequencer.publish(2);
        }

        @Actor
        public void actor2(final ZZ_Result r)
        {
            r.r2 = sequencer.isAvailable(2);
            r.r1 = sequencer.isAvailable(1);
        }
    }

    /**
     * The isAvailable implementation is volatile so we should never see an update to it without seeing the update to a
     * previously set value also.
     *
     * <p>If the value was not volatile there would be no ordering rules stopping it being seen updated before the
     * other value.
     */
    @JCStressTest
    @Outcome(id = "false, false", expect = ACCEPTABLE, desc = "Doing both reads early.")
    @Outcome(id = "true, true", expect = ACCEPTABLE, desc = "Doing both reads late.")
    @Outcome(id = "false, true", expect = ACCEPTABLE, desc = "Caught in the middle: $x is visible, $y is not.")
    @Outcome(id = "true, false", expect = FORBIDDEN, desc = "Seeing $y, but not $x!")
    @State
    public static class GetVolatile
    {
        boolean x = false;
        Sequencer y = createSequencer();

        @Actor
        public void actor1()
        {
            x = true;
            y.publish(1);
        }

        @Actor
        public void actor2(final ZZ_Result r)
        {
            r.r1 = y.isAvailable(1);
            r.r2 = x;
        }
    }

    /**
     * In absence of synchronization, the order of independent reads is undefined.
     * In our case, the read of isAvailable is volatile which mandates the writes to the same
     * variable to be observed in a total order (that implies that _observers_ are also ordered)
     */
    @JCStressTest
    @Outcome(id = "false, false", expect = ACCEPTABLE, desc = "Doing both reads early.")
    @Outcome(id = "true, true", expect = ACCEPTABLE, desc = "Doing both reads late.")
    @Outcome(id = "false, true", expect = ACCEPTABLE, desc = "Doing first read early, not surprising.")
    @Outcome(id = "true, false", expect = FORBIDDEN, desc = "Violates coherence.")
    @State
    public static class SameVolatileRead
    {
        private final Holder h1 = new Holder();
        private final Holder h2 = h1;

        private static class Holder
        {
            Sequencer sequence = createSequencer();
        }

        @Actor
        public void actor1()
        {
            h1.sequence.publish(1);
        }

        @Actor
        public void actor2(final ZZ_Result r)
        {
            Holder h1 = this.h1;
            Holder h2 = this.h2;

            r.r1 = h1.sequence.isAvailable(1);
            r.r2 = h2.sequence.isAvailable(1);
        }
    }
}
