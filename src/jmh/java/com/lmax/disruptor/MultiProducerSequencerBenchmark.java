package com.lmax.disruptor;

import com.lmax.disruptor.alternatives.MultiProducerSequencerUnsafe;
import com.lmax.disruptor.alternatives.MultiProducerSequencerVarHandle;
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

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
public class MultiProducerSequencerBenchmark
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
     * com.lmax.disruptor.alternatives.MultiProducerSequencerUnsafe (as of disruptor v3.4.2)
     */
    @State(Scope.Group)
    public static class StateMultiProducerSequencerUnsafe
    {
        Sequencer value1 = new MultiProducerSequencerUnsafe(64, new BlockingWaitStrategy());
        Sequencer value2 = new MultiProducerSequencerUnsafe(64, new BlockingWaitStrategy());
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public boolean read1(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        return s.value1.isAvailable(1);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public boolean read2(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        return s.value1.isAvailable(1);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue1A(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        s.value1.publish(1L);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue1B(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        s.value1.publish(2L);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue2A(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        s.value2.publish(1L);
    }

    @Benchmark
    @Group("SequenceUnsafe")
    public void setValue2B(final StateMultiProducerSequencerUnsafe s, final ThreadPinningState t)
    {
        s.value2.publish(2L);
    }

    /*
     * com.lmax.disruptor.alternatives.StateSequenceVarHandle (as of disruptor v3.4.2)
     */
    @State(Scope.Group)
    public static class StateMultiProducerSequencerVarHandle
    {
        Sequencer value1 = new MultiProducerSequencerVarHandle(64, new BlockingWaitStrategy());
        Sequencer value2 = new MultiProducerSequencerVarHandle(64, new BlockingWaitStrategy());
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public boolean read1(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        return s.value1.isAvailable(1);
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public boolean read2(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        return s.value1.isAvailable(1);
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public void setValue1A(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        s.value1.publish(1L);
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public void setValue1B(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        s.value1.publish(2L);
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public void setValue2A(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        s.value2.publish(1L);
    }

    @Benchmark
    @Group("StateMultiProducerSequencerVarHandle")
    public void setValue2B(final StateMultiProducerSequencerVarHandle s, final ThreadPinningState t)
    {
        s.value2.publish(2L);
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(MultiProducerSequencerBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
