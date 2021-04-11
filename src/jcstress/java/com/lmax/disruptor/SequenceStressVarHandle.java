package com.lmax.disruptor;

import com.lmax.disruptor.alternatives.SequenceVarHandle;
import org.openjdk.jcstress.annotations.Actor;
import org.openjdk.jcstress.annotations.Arbiter;
import org.openjdk.jcstress.annotations.JCStressTest;
import org.openjdk.jcstress.annotations.Outcome;
import org.openjdk.jcstress.annotations.Ref;
import org.openjdk.jcstress.annotations.State;
import org.openjdk.jcstress.infra.results.JJ_Result;
import org.openjdk.jcstress.infra.results.J_Result;
import org.openjdk.jcstress.infra.results.ZZJ_Result;

import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE;
import static org.openjdk.jcstress.annotations.Expect.ACCEPTABLE_INTERESTING;
import static org.openjdk.jcstress.annotations.Expect.FORBIDDEN;

public class SequenceStressVarHandle
{
    /**
     * `SequenceVarHandle::incrementAndGet` is atomic and should never lose an update, even with multiple threads racing.
     */
    @JCStressTest
    @Outcome(id = "1", expect = FORBIDDEN, desc = "One update lost.")
    @Outcome(id = "2", expect = ACCEPTABLE, desc = "Both updates.")
    @State
    public static class IncrementAndGet
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void actor1()
        {
            sequence.incrementAndGet();
        }

        @Actor
        public void actor2()
        {
            sequence.incrementAndGet();
        }

        @Arbiter
        public void arbiter(final J_Result r)
        {
            r.r1 = sequence.get();
        }
    }

    /**
     * `SequenceVarHandle::compareAndSet` is atomic and should never lose an update, even with multiple threads racing.
     */
    @JCStressTest
    @Outcome(id = {"true, false, 10", "false, true, 20"}, expect = ACCEPTABLE, desc = "Either updated.")
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @State
    public static class CompareAndSet
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void actor1(final ZZJ_Result r)
        {
            r.r1 = sequence.compareAndSet(0, 10);
        }

        @Actor
        public void actor2(final ZZJ_Result r)
        {
            r.r2 = sequence.compareAndSet(0, 20);
        }

        @Arbiter
        public void arbiter(final ZZJ_Result r)
        {
            r.r3 = sequence.get();
        }
    }

    /**
     * `SequenceVarHandle::addAndGet` is atomic and should never lose an update, even with multiple threads racing.
     */
    @JCStressTest
    @Outcome(id = "10", expect = FORBIDDEN, desc = "One update lost.")
    @Outcome(id = "20", expect = FORBIDDEN, desc = "One update lost.")
    @Outcome(id = "30", expect = ACCEPTABLE, desc = "Both updates.")
    @State
    public static class AddAndGet
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void actor1()
        {
            sequence.addAndGet(10);
        }

        @Actor
        public void actor2()
        {
            sequence.addAndGet(20);
        }

        @Arbiter
        public void arbiter(final J_Result r)
        {
            r.r1 = sequence.get();
        }
    }

    /**
     * Updates to non-volatile long values in Java are issued as two separate 32-bit writes.
     * SequenceVarHandle should store its underlying value as a volatile long and therefore should not experience this effect
     * even when a non-volatile UNSAFE set method is used.
     */
    @JCStressTest
    @Outcome(id = "0", expect = ACCEPTABLE, desc = "Seeing the default value: writer had not acted yet.")
    @Outcome(id = "-1", expect = ACCEPTABLE, desc = "Seeing the full value.")
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @Ref("https://docs.oracle.com/javase/specs/jls/se11/html/jls-17.html#jls-17.7")
    @State
    public static class LongFullSet
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void writer()
        {
            sequence.set(0xFFFFFFFF_FFFFFFFFL);
        }

        @Actor
        public void reader(final J_Result r)
        {
            r.r1 = sequence.get();
        }
    }

    /**
     * Updates to non-volatile long values in Java are issued as two separate 32-bit writes.
     * SequenceVarHandle should store its underlying value as a volatile long and therefore should not experience this effect.
     */
    @JCStressTest
    @Outcome(id = "0", expect = ACCEPTABLE, desc = "Seeing the default value: writer had not acted yet.")
    @Outcome(id = "-1", expect = ACCEPTABLE, desc = "Seeing the full value.")
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @Ref("https://docs.oracle.com/javase/specs/jls/se11/html/jls-17.html#jls-17.7")
    @State
    public static class LongFullSetVolatile
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void writer()
        {
            sequence.setVolatile(0xFFFFFFFF_FFFFFFFFL);
        }

        @Actor
        public void reader(final J_Result r)
        {
            r.r1 = sequence.get();
        }
    }

    /**
     * Updates to non-volatile long values in Java are issued as two separate 32-bit writes.
     * SequenceVarHandle should store its underlying value as a volatile long and therefore should not experience this effect.
     */
    @JCStressTest
    @Outcome(id = "0", expect = ACCEPTABLE, desc = "Seeing the default value: writer had not acted yet.")
    @Outcome(id = "-1", expect = ACCEPTABLE, desc = "Seeing the full value.")
    @Outcome(expect = FORBIDDEN, desc = "Other cases are forbidden.")
    @Ref("https://docs.oracle.com/javase/specs/jls/se11/html/jls-17.html#jls-17.7")
    @State
    public static class LongFullCompareAndSet
    {
        SequenceVarHandle sequence = new SequenceVarHandle(0);

        @Actor
        public void writer()
        {
            sequence.compareAndSet(0, 0xFFFFFFFF_FFFFFFFFL);
        }

        @Actor
        public void reader(final J_Result r)
        {
            r.r1 = sequence.get();
        }
    }


    /**
     * In absence of synchronization, the order of independent reads is undefined.
     * In our case, the value in SequenceVarHandle is volatile which mandates the writes to the same
     * variable to be observed in a total order (that implies that _observers_ are also ordered)
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Doing both reads early.")
    @Outcome(id = "1, 1", expect = ACCEPTABLE, desc = "Doing both reads late.")
    @Outcome(id = "0, 1", expect = ACCEPTABLE, desc = "Doing first read early, not surprising.")
    @Outcome(id = "1, 0", expect = FORBIDDEN, desc = "Violates coherence.")
    @State
    public static class SameVolatileRead
    {
        private final Holder h1 = new Holder();
        private final Holder h2 = h1;

        private static class Holder
        {
            SequenceVarHandle sequence = new SequenceVarHandle(0);
        }

        @Actor
        public void actor1()
        {
            h1.sequence.set(1);
        }

        @Actor
        public void actor2(final JJ_Result r)
        {
            Holder h1 = this.h1;
            Holder h2 = this.h2;

            r.r1 = h1.sequence.get();
            r.r2 = h2.sequence.get();
        }
    }


    /**
     * The value field in SequenceVarHandle is volatile so we should never see an update to it without seeing the update to a
     * previously set value also.
     *
     * <p>If the value was not volatile there would be no ordering rules stopping it being seen updated before the
     * other value.
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Doing both reads early.")
    @Outcome(id = "1, 1", expect = ACCEPTABLE, desc = "Doing both reads late.")
    @Outcome(id = "0, 1", expect = ACCEPTABLE, desc = "Caught in the middle: $x is visible, $y is not.")
    @Outcome(id = "1, 0", expect = FORBIDDEN, desc = "Seeing $y, but not $x!")
    @State
    public static class SetVolatileGuard
    {
        long x = 0;
        SequenceVarHandle y = new SequenceVarHandle(0);

        @Actor
        public void actor1()
        {
            x = 1;
            y.setVolatile(1);
        }

        @Actor
        public void actor2(final JJ_Result r)
        {
            r.r1 = y.get();
            r.r2 = x;
        }
    }

    /**
     * The value field in SequenceVarHandle is volatile so we should never see an update to it without seeing the update to a
     * previously set value also.
     *
     * <p>If the value was not volatile there would be no ordering rules stopping it being seen updated before the
     * other value.
     *
     * <p>This is a property of the field, not a property of the method used to set the value of it.
     */
    @JCStressTest
    @Outcome(id = "0, 0", expect = ACCEPTABLE, desc = "Doing both reads early.")
    @Outcome(id = "1, 1", expect = ACCEPTABLE, desc = "Doing both reads late.")
    @Outcome(id = "0, 1", expect = ACCEPTABLE, desc = "Caught in the middle: $x is visible, $y is not.")
    @Outcome(id = "1, 0", expect = FORBIDDEN, desc = "Seeing $y, but not $x!")
    @State
    public static class SetGuard
    {
        long x = 0;
        SequenceVarHandle y = new SequenceVarHandle(0);

        @Actor
        public void actor1()
        {
            x = 1;
            y.set(1);
        }

        @Actor
        public void actor2(final JJ_Result r)
        {
            r.r1 = y.get();
            r.r2 = x;
        }
    }


    /**
     * Volatile setting will experience total ordering.
     */
    @JCStressTest
    @Outcome(id = {"0, 1", "1, 0", "1, 1"}, expect = ACCEPTABLE, desc = "Trivial under sequential consistency")
    @Outcome(id = "0, 0", expect = FORBIDDEN, desc = "Violates sequential consistency")
    @State
    public static class SetVolatileDekker
    {
        SequenceVarHandle x = new SequenceVarHandle(0);
        SequenceVarHandle y = new SequenceVarHandle(0);

        @Actor
        public void actor1(final JJ_Result r)
        {
            x.setVolatile(1);
            r.r1 = y.get();
        }

        @Actor
        public void actor2(final JJ_Result r)
        {
            y.setVolatile(1);
            r.r2 = x.get();

        }
    }

    /**
     * Non-volatile setting will not experience total ordering, those gets can be re-ordered and happen before either set.
     */
    @JCStressTest
    @Outcome(id = {"0, 1", "1, 0", "1, 1"}, expect = ACCEPTABLE, desc = "Trivial under sequential consistency")
    @Outcome(id = "0, 0", expect = ACCEPTABLE_INTERESTING, desc = "Violates sequential consistency")
    @State
    public static class SetDekker
    {
        SequenceVarHandle x = new SequenceVarHandle(0);
        SequenceVarHandle y = new SequenceVarHandle(0);

        @Actor
        public void actor1(final JJ_Result r)
        {
            x.set(1);
            r.r1 = y.get();
        }

        @Actor
        public void actor2(final JJ_Result r)
        {
            y.set(1);
            r.r2 = x.get();
        }
    }
}
