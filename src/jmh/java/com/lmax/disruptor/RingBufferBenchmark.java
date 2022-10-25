package com.lmax.disruptor;

import com.lmax.disruptor.alternatives.RingBufferArray;
import com.lmax.disruptor.alternatives.RingBufferUnsafe;
import com.lmax.disruptor.support.DummyWaitStrategy;
import com.lmax.disruptor.support.StubEvent;
import net.openhft.affinity.Affinity;
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
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@SuppressWarnings("ALL")
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
public class RingBufferBenchmark
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
            else
            {
                System.err.printf("ISOLATED_CPUS environment variable not defined, running thread %s (id=%d) on scheduler-defined CPU:%d%n ",
                        Thread.currentThread().getName(),
                        threadId,
                        Affinity.getCpu());
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
     * APPROACH 1: RingBufferUnsafe - Using the unsafe API to avoid bounds-checking, as was the case for Disruptor 3.x
     */

    @State(Scope.Group)
    public static class StateRingBufferUnsafe
    {
        RingBufferUnsafe<Object> ringBufferUnsafe = new RingBufferUnsafe<>(
                () -> new StubEvent(-1),
                new SingleProducerSequencer(128, new DummyWaitStrategy()));
    }

    @Benchmark
    @Group("RingBufferUnsafe")
    public Object readUnsafe(final StateRingBufferUnsafe ringBufferUnsafe, final ThreadPinningState t)
    {
        return ringBufferUnsafe.ringBufferUnsafe.get(64);
    }

    @Benchmark
    @Group("RingBufferUnsafe")
    public void writeUnsafe(final StateRingBufferUnsafe ringBufferUnsafe, final ThreadPinningState t)
    {
        ringBufferUnsafe.ringBufferUnsafe.publish(64);
    }

    /*
     * APPROACH 2: RingBufferArray - There is no support for non-bounds-checked array element access as there was via
     * unsafe. So the simplest approach is to go back to a plain array and index to elements.
     */

    @State(Scope.Group)
    public static class StateRingBufferArray
    {
        RingBufferArray<Object> ringBufferVarHandle = new RingBufferArray<>(
                () -> new StubEvent(-1),
                new SingleProducerSequencer(128, new DummyWaitStrategy())
        );
    }

    @Benchmark
    @Group("RingBufferArray")
    public Object readArray(final StateRingBufferArray ringBufferVarHandle, final ThreadPinningState t)
    {
        return ringBufferVarHandle.ringBufferVarHandle.get(64);
    }

    @Benchmark
    @Group("RingBufferArray")
    public void writeArray(final StateRingBufferArray ringBufferVarHandle, final ThreadPinningState t)
    {
        ringBufferVarHandle.ringBufferVarHandle.publish(64);
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(RingBufferBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
