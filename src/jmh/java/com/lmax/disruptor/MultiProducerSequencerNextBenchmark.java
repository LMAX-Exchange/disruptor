package com.lmax.disruptor;

import net.openhft.affinity.Affinity;
import net.openhft.affinity.AffinityLock;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Control;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Throughput of {@link MultiProducerSequencer#next()} with N producer threads (default 2, set with {@code -tg 1,N})
 * contending on the same sequencer,
 * while a third thread acts as the consumer: it follows the highest published sequence and advances its gating
 * sequence so producers are only held back by a real full ring.
 *
 * <p>Each producer op is {@code next()} + {@code publish()}; publishing is required for the consumer to make
 * progress, so it cannot be left out without the producers wrapping and blocking.
 *
 * <p>{@code next()} spins until capacity is available, so the run is shut down via {@link Control}: once measurement
 * stops, producers stop claiming and the consumer releases its gating sequence to the cursor, freeing any producer
 * still spinning. JMH keeps invoking the consumer during warmdown until every thread has finished.
 *
 * <p>To run (from the repo root):
 * <pre>
 * ./gradlew jmhJar
 *
 * # Busy producers only: -tg consumer,producer
 * java -jar build/libs/disruptor-*-jmh.jar MultiProducerSequencerNextBenchmark.ProducersOneConsumer \
 *   -p bufferSize=1024,65536 -tg 1,2
 *
 * # Busy producers plus mostly-idle publishers: -tg busyProducer,consumerWithIdle,idlePublisher
 * java -jar build/libs/disruptor-*-jmh.jar MultiProducerSequencerNextBenchmark.BusyAndIdleProducers \
 *   -p bufferSize=1024,65536 -p idleIntervalNanos=1000000 -tg 2,1,16
 * </pre>
 * {@code -tg} counts follow the group's method names in alphabetical order. {@code bufferSize} must be a power of
 * 2. For quick runs add {@code -f 1 -wi 1 -i 1}; omit for the full 2 forks, 5+5 iterations. Read the
 * {@code :producer} / {@code :busyProducer} lines (totals across those threads); the group total is dominated by
 * the consumer's empty polls. Large idle-publisher counts can overwhelm a small machine. To pin threads, set
 * {@code ISOLATED_CPUS} (needs one CPU per pinned thread; idle publishers are never pinned).
 */
@SuppressWarnings("unused")
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class MultiProducerSequencerNextBenchmark
{
    // To run this on a tuned system with benchmark threads pinned to isolated cpus:
    // Run the JMH process with an env var defining the isolated cpu list, e.g. ISOLATED_CPUS=38,40,42 java -jar disruptor-jmh.jar
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
                if (threadId >= ISOLATED_CPUS.size())
                {
                    throw new IllegalArgumentException(
                            String.format("Benchmark uses at least %d threads, only defined %d isolated cpus",
                                    threadId + 1,
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

    @State(Scope.Group)
    public static class SequencerState
    {
        @Param({"1024"})
        int bufferSize;

        MultiProducerSequencer sequencer;
        Sequence consumerSequence;

        @Setup
        public void setup()
        {
            sequencer = new MultiProducerSequencer(bufferSize, new BusySpinWaitStrategy());
            consumerSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
            sequencer.addGatingSequences(consumerSequence);
        }
    }

    @State(Scope.Thread)
    public static class IdlePublisherState
    {
        @Param({"1000000"})
        long idleIntervalNanos;

        long nextPublishNanos;

        @Setup
        public void setup()
        {
            nextPublishNanos = System.nanoTime() + idleIntervalNanos;
        }
    }

    /**
     * Producer thread count defaults to 2; override with {@code -tg 1,N} (consumer threads, producer threads —
     * JMH orders group members alphabetically by method name).
     */
    @Benchmark
    @Group("ProducersOneConsumer")
    @GroupThreads(2)
    public long producer(final SequencerState s, final ThreadPinningState t, final Control control)
    {
        return claimAndPublish(s, control);
    }

    @Benchmark
    @Group("ProducersOneConsumer")
    @GroupThreads(1)
    public long consumer(final SequencerState s, final ThreadPinningState t, final Control control)
    {
        return consume(s, control);
    }

    /*
     * Busy producers sharing the sequencer with many mostly-idle publishers, each publishing once per
     * idleIntervalNanos (default 1ms). Compare the busyProducer score with ProducersOneConsumer:producer to see the
     * impact of the idle traffic.
     *
     * Thread counts are set with -tg B,C,I (busyProducer, consumerWithIdle, idlePublisher — alphabetical by method
     * name), default 2,1,32. Idle publishers are never pinned, so ISOLATED_CPUS only needs to cover B + C threads.
     */

    @Benchmark
    @Group("BusyAndIdleProducers")
    @GroupThreads(2)
    public long busyProducer(final SequencerState s, final ThreadPinningState t, final Control control)
    {
        return claimAndPublish(s, control);
    }

    @Benchmark
    @Group("BusyAndIdleProducers")
    @GroupThreads(32)
    public long idlePublisher(final SequencerState s, final IdlePublisherState idle, final Control control)
    {
        // Fixed-rate schedule: wait until the next deadline rather than sleeping a fixed time after each publish.
        long now;
        while ((now = System.nanoTime()) < idle.nextPublishNanos)
        {
            if (control.stopMeasurement)
            {
                return -1;
            }
            LockSupport.parkNanos(idle.nextPublishNanos - now);
        }
        // If we've fallen behind (e.g. stalled in next()), skip missed slots rather than bursting to catch up.
        idle.nextPublishNanos = Math.max(idle.nextPublishNanos, now) + idle.idleIntervalNanos;

        return claimAndPublish(s, control);
    }

    @Benchmark
    @Group("BusyAndIdleProducers")
    @GroupThreads(1)
    public long consumerWithIdle(final SequencerState s, final ThreadPinningState t, final Control control)
    {
        return consume(s, control);
    }

    private static long claimAndPublish(final SequencerState s, final Control control)
    {
        if (control.stopMeasurement)
        {
            return -1;
        }

        final long sequence = s.sequencer.next();
        s.sequencer.publish(sequence);
        return sequence;
    }

    private static long consume(final SequencerState s, final Control control)
    {
        if (control.stopMeasurement)
        {
            // Release any producer still spinning in next() so the iteration can finish.
            final long cursor = s.sequencer.getCursor();
            s.consumerSequence.set(cursor);
            return cursor;
        }

        final long next = s.consumerSequence.get() + 1;
        final long available = s.sequencer.getHighestPublishedSequence(next, s.sequencer.getCursor());
        if (available >= next)
        {
            s.consumerSequence.set(available);
        }
        return available;
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(MultiProducerSequencerNextBenchmark.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
