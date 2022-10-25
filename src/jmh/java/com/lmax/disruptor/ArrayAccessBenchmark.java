package com.lmax.disruptor;

import com.lmax.disruptor.util.SimpleEvent;
import com.lmax.disruptor.util.UnsafeAccess;
import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
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
import sun.misc.Unsafe;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@SuppressWarnings("unused")
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@Threads(1)
@State(Scope.Thread)
public class ArrayAccessBenchmark
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

    private static final int EVENT_COUNT = 64;
    private static final int INDEX_MASK = EVENT_COUNT - 1;
    private final Object[] entries = new Object[EVENT_COUNT];
    public int sequence;

    private static final Unsafe UNSAFE = UnsafeAccess.getUnsafe();
    private final int scale = UNSAFE.arrayIndexScale(Object[].class);
    private final int offset = UNSAFE.arrayBaseOffset(Object[].class);

    private final VarHandle varHandle = MethodHandles.arrayElementVarHandle(Object[].class);

    private final MethodHandle methodHandle = MethodHandles.arrayElementGetter(Object[].class);

    @Setup
    public void setup()
    {
        for (int i = 0; i < EVENT_COUNT; i++)
        {
            SimpleEvent simpleEvent = new SimpleEvent();
            simpleEvent.setValue(i);
            entries[i] = simpleEvent;
        }

        sequence = 0;
    }

    @Benchmark
    public Object standardArrayAccess(final ThreadPinningState t)
    {
        return entries[getNextSequence()];
    }

    @Benchmark
    public Object unsafeArrayAccess(final ThreadPinningState t)
    {
        return UNSAFE.getObject(entries, offset + ((long) (getNextSequence()) * scale));
    }

    @Benchmark
    public Object varHandleArrayAccess(final ThreadPinningState t)
    {
        return varHandle.get(entries, getNextSequence());
    }

    @Benchmark
    public Object getterMethodHandleInvokeArrayAccess(final ThreadPinningState t) throws Throwable
    {
        return methodHandle.invoke(entries, getNextSequence());
    }

    @Benchmark
    public Object getterMethodHandleInvokeExactArrayAccess(final ThreadPinningState t) throws Throwable
    {
        return methodHandle.invokeExact(entries, getNextSequence());
    }

    private int getNextSequence()
    {
        return sequence++ & INDEX_MASK;
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(ArrayAccessBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
