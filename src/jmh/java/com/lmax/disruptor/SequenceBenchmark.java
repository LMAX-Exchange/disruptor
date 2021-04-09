package com.lmax.disruptor;

import com.lmax.disruptor.alternatives.SequenceDoublePadded;
import com.lmax.disruptor.alternatives.SequenceUnsafe;
import com.lmax.disruptor.alternatives.SequenceVarHandle;
import com.lmax.disruptor.alternatives.SequenceVarHandleArray;
import com.lmax.disruptor.alternatives.SequenceVarHandleBarrier;
import net.openhft.affinity.AffinityLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
public class SequenceBenchmark
{
    // To run this on a tuned system with benchmark threads pinned to isolated cpus:
    // Run the JMH process with an env var defining the isolated cpu list, e.g. ISOLATED_CPUS=38,40,42,44,46,48 java -jar disruptor-jmh.jar
    private static final List<Integer> ISOLATED_CPUS = Arrays.stream(System.getenv().getOrDefault("ISOLATED_CPUS", "").split(","))
            .map(String::trim)
            .filter(not(String::isBlank))
            .map(Integer::valueOf)
            .collect(Collectors.toList());

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger();

    @State(Scope.Thread)
    public static class ThreadPinningState
    {
        int threadId = THREAD_COUNTER.getAndIncrement();
        private AffinityLock affinityLock;

        @Setup
        public void setup()
        {
            if (ISOLATED_CPUS.size() > 0)
            {
                if (threadId > ISOLATED_CPUS.size())
                {
                    throw new IllegalArgumentException(
                            String.format("Benchmark uses at least %d threads, only defined %d isolated cpus",
                                    threadId,
                                    ISOLATED_CPUS.size()
                            ));
                }

                final Integer cpuId = ISOLATED_CPUS.get(threadId);
                affinityLock = AffinityLock.acquireLock(cpuId);
                System.out.printf("Attempted to set thread affinity for %s to %d, success = %b%n",
                        Thread.currentThread().getName(),
                        cpuId,
                        affinityLock.isAllocated()
                );
            }
        }

        @TearDown
        public void teardown()
        {
            if (ISOLATED_CPUS.size() > 0)
            {
                affinityLock.release();
            }
        }
    }

    /*
     * APPROACH 1: AtomicLong
     *
     * Thread safe? Check. Atomic updates? Check.
     */
    @State(Scope.Group)
    public static class StateAtomic
    {
        AtomicLong value1 = new AtomicLong(0);
        AtomicLong value2 = new AtomicLong(0);
    }

    @Benchmark
    @Group("AtomicLong")
    public long read1(final StateAtomic s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("AtomicLong")
    public long read2(final StateAtomic s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("AtomicLong")
    public void setValue1Opaque(final StateAtomic s, final ThreadPinningState t)
    {
        // Put Long Opaque
        s.value1.setOpaque(1234L);
    }

    @Benchmark
    @Group("AtomicLong")
    public void setValue1Volatile(final StateAtomic s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.set(5678L);
    }

    @Benchmark
    @Group("AtomicLong")
    public long incrementValue2(final StateAtomic s, final ThreadPinningState t)
    {
        return s.value2.getAndIncrement();
    }

    /*
     * APPROACH 2: com.lmax.disruptor.Sequence (as of disruptor v3.4.2)
     *
     * A lot like AtomicLong, but with some padding to avoid false sharing.
     * This uses UNSAFE to give us more control over the memory model of the field, we don't always need full volatile
     * guarantees and we need to use compareAndSwap to be atomic.
     */
    @State(Scope.Group)
    public static class StateSequenceUnsafe
    {
        SequenceUnsafe value1 = new SequenceUnsafe(0);
        SequenceUnsafe value2 = new SequenceUnsafe(0);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public long read1(final StateSequenceUnsafe s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public long read2(final StateSequenceUnsafe s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue1(final StateSequenceUnsafe s, final ThreadPinningState t)
    {
        // Put Ordered Long
        s.value1.set(1234L);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue1Volatile(final StateSequenceUnsafe s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.setVolatile(5678L);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public long incrementValue2(final StateSequenceUnsafe s, final ThreadPinningState t)
    {
        return s.value2.incrementAndGet();
    }

    /*
     * APPROACH 2.5: com.lmax.disruptor.alternatives.SequenceDoublePadded
     *
     * This is identical to the Sequence from Disruptor 3.4.2 but with double the amount of padding.
     * https://github.com/LMAX-Exchange/disruptor/issues/231 raised the point of Intel CPUs optionally (on by default I
     * believe) prefetching 2 cache lines.
     *
     * This benchmark should show if there is any difference in performance having extra padding when compared to the
     * regular Sequence benchmark.
     */
    @State(Scope.Group)
    public static class StateSequenceDoublePadded
    {
        SequenceDoublePadded value1 = new SequenceDoublePadded(0);
        SequenceDoublePadded value2 = new SequenceDoublePadded(0);
    }

    @Benchmark
    @Group("SequenceDoublePadded")
    public long read1(final StateSequenceDoublePadded s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("SequenceDoublePadded")
    public long read2(final StateSequenceDoublePadded s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("SequenceDoublePadded")
    public void setValue1(final StateSequenceDoublePadded s, final ThreadPinningState t)
    {
        // Put Ordered Long
        s.value1.set(1234L);
    }

    @Benchmark
    @Group("SequenceDoublePadded")
    public void setValue1Volatile(final StateSequenceDoublePadded s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.setVolatile(5678L);
    }

    @Benchmark
    @Group("SequenceDoublePadded")
    public long incrementValue2(final StateSequenceDoublePadded s, final ThreadPinningState t)
    {
        return s.value2.incrementAndGet();
    }

    /*
     * APPROACH 3: com.lmax.disruptor.alternatives.SequenceVarHandle
     *
     * An updated version of com.lmax.disruptor.Sequence but using VarHandle instead of UNSAFE to get memory ordering.
     * This is probably the way we should go for version Disruptor 4.0
     */
    @State(Scope.Group)
    public static class StateSequenceVarHandle
    {
        SequenceVarHandle value1 = new SequenceVarHandle(0);
        SequenceVarHandle value2 = new SequenceVarHandle(0);
    }

    @Benchmark
    @Group("SequenceVarHandle")
    public long read1(final StateSequenceVarHandle s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("SequenceVarHandle")
    public long read2(final StateSequenceVarHandle s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("SequenceVarHandle")
    public void setValue1(final StateSequenceVarHandle s, final ThreadPinningState t)
    {
        // Put Ordered Long
        s.value1.set(1234L);
    }

    @Benchmark
    @Group("SequenceVarHandle")
    public void setValue1Volatile(final StateSequenceVarHandle s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.setVolatile(5678L);
    }

    @Benchmark
    @Group("SequenceVarHandle")
    public long incrementValue2(final StateSequenceVarHandle s, final ThreadPinningState t)
    {
        return s.value2.incrementAndGet();
    }

    /*
     * APPROACH 3.5: com.lmax.disruptor.alternatives.SequenceVarHandleBarrier
     *
     * Much like the VarHandle version but with manual memory barriers used.
     * We think this might cut down on some boxing and maybe gives a little more flexibility.
     */
    @State(Scope.Group)
    public static class StateSequenceVarHandleBarrier
    {
        SequenceVarHandleBarrier value1 = new SequenceVarHandleBarrier(0);
        SequenceVarHandleBarrier value2 = new SequenceVarHandleBarrier(0);
    }

    @Benchmark
    @Group("SequenceVarHandleBarrier")
    public long read1(final StateSequenceVarHandleBarrier s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("SequenceVarHandleBarrier")
    public long read2(final StateSequenceVarHandleBarrier s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("SequenceVarHandleBarrier")
    public void setValue1(final StateSequenceVarHandleBarrier s, final ThreadPinningState t)
    {
        // Put Ordered Long
        s.value1.set(1234L);
    }

    @Benchmark
    @Group("SequenceVarHandleBarrier")
    public void setValue1Volatile(final StateSequenceVarHandleBarrier s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.setVolatile(5678L);
    }

    @Benchmark
    @Group("SequenceVarHandleBarrier")
    public long incrementValue2(final StateSequenceVarHandleBarrier s, final ThreadPinningState t)
    {
        return s.value2.incrementAndGet();
    }

    /*
     * APPROACH 4: com.lmax.disruptor.alternatives.SequenceVarHandleArray
     *
     * Similar to the SequenceVarHandle but instead of using class hierarchy for padding, using a long array.
     * This seemed like a good idea but suffers from array bounds checking slowing down all the operations.
     * This method probably isn't a good way to go, but kept here as a warning to others who think this is a good way to
     * do cache-line padding.
     */
    @State(Scope.Group)
    public static class StateSequenceVarHandleArray
    {
        SequenceVarHandleArray value1 = new SequenceVarHandleArray(0);
        SequenceVarHandleArray value2 = new SequenceVarHandleArray(0);
    }

    @Benchmark
    @Group("SequenceVarHandleArray")
    public long read1(final StateSequenceVarHandleArray s, final ThreadPinningState t)
    {
        return s.value1.get();
    }

    @Benchmark
    @Group("SequenceVarHandleArray")
    public long read2(final StateSequenceVarHandleArray s, final ThreadPinningState t)
    {
        return s.value2.get();
    }

    @Benchmark
    @Group("SequenceVarHandleArray")
    public void setValue1(final StateSequenceVarHandleArray s, final ThreadPinningState t)
    {
        // Put Ordered Long
        s.value1.set(1234L);
    }

    @Benchmark
    @Group("SequenceVarHandleArray")
    public void setValue1Volatile(final StateSequenceVarHandleArray s, final ThreadPinningState t)
    {
        // Put Long Volatile
        s.value1.setVolatile(5678L);
    }

    @Benchmark
    @Group("SequenceVarHandleArray")
    public long incrementValue2(final StateSequenceVarHandleArray s, final ThreadPinningState t)
    {
        return s.value2.incrementAndGet();
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(SequenceBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
