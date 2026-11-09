package com.lmax.disruptor;

import net.openhft.affinity.AffinityLock;
import org.agrona.BitUtil;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.AtomicBuffer;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.MessageHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.agrona.concurrent.ringbuffer.OneToOneRingBuffer;
import org.agrona.concurrent.ringbuffer.RecordDescriptor;
import org.agrona.concurrent.ringbuffer.RingBuffer;
import org.agrona.concurrent.ringbuffer.RingBufferDescriptor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Burst benchmark for Agrona's {@link OneToOneRingBuffer}, for comparison with {@link DisruptorBurstBenchmark}.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "-Xmx2g",
    "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
    "-Dagrona.disable.bounds.checks=true",
    "-XX:+UseZGC",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class OneToOneRingBufferBurstBenchmark
{
    static final int CONSUMER_CPU = 1;
    static final int PRODUCER_CPU = 3;
    static final int MESSAGE_COUNT_LIMIT = 256;
    static final int RING_BUFFER_CAPACITY = DisruptorBurstBenchmark.RING_BUFFER_SIZE;
    static final Path RESULT_FILE = Path.of("build", "reports", "jmh", "OneToOneRingBufferBurstBenchmark.json");

    @Param({"BusySpinIdleStrategy"})
    String idleStrategyClassName;

    @Param({"1", "32", "64", "128"})
    int burstLength;

    @Param({"8", "128", "256"})
    int messageLength;

    /** Work the consumer does per message, in {@link Blackhole#consumeCPU} tokens. */
    @Param({"0", "24", "48", "96"})
    long consumerTokens;

    private final List<AffinityLock> affinityLocks = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Sequence burstsConsumed = new Sequence(0);

    private RingBuffer ringBuffer;
    private Thread consumerThread;
    private byte[] message;
    private long burstsPublished;

    @Setup
    public void setup(final Blackhole blackhole)
    {
        affinityLocks.add(AffinityLock.acquireLock(PRODUCER_CPU));

        final int recordLength = BitUtil.align(RecordDescriptor.HEADER_LENGTH + messageLength, RecordDescriptor.ALIGNMENT);
        final int capacity = Integer.highestOneBit(RING_BUFFER_CAPACITY * recordLength);
        ringBuffer = new OneToOneRingBuffer(new UnsafeBuffer(new byte[capacity + RingBufferDescriptor.TRAILER_LENGTH]));
        message = new byte[messageLength];

        final Subscriber subscriber = new Subscriber(
            createIdleStrategyByName(idleStrategyClassName), ringBuffer, running, burstLength, consumerTokens, blackhole,
            burstsConsumed);
        consumerThread = new Thread(() ->
        {
            affinityLocks.add(AffinityLock.acquireLock(CONSUMER_CPU));
            subscriber.run();
        }, "consumer");
        consumerThread.start();
    }

    @TearDown
    public void tearDown() throws Exception
    {
        affinityLocks.forEach(AffinityLock::release);
        running.set(false);
        consumerThread.join();
    }

    public static class Subscriber implements Runnable, MessageHandler
    {
        private final IdleStrategy idleStrategy;
        private final RingBuffer ringBuffer;
        private final AtomicBoolean running;
        private final int burstLength;
        private final long consumerTokens;
        private final Blackhole blackhole;
        private final Sequence burstsConsumed;
        private int messagesInBurst;
        private long burstsHandled;

        Subscriber(
            final IdleStrategy idleStrategy,
            final RingBuffer ringBuffer,
            final AtomicBoolean running,
            final int burstLength,
            final long consumerTokens,
            final Blackhole blackhole,
            final Sequence burstsConsumed)
        {
            this.idleStrategy = idleStrategy;
            this.ringBuffer = ringBuffer;
            this.running = running;
            this.burstLength = burstLength;
            this.consumerTokens = consumerTokens;
            this.blackhole = blackhole;
            this.burstsConsumed = burstsConsumed;
        }

        @Override
        public void run()
        {
            while (true)
            {
                final int msgCount = ringBuffer.read(this, MESSAGE_COUNT_LIMIT);
                idleStrategy.idle(msgCount);
                if (0 == msgCount && !running.get())
                {
                    break;
                }
            }
        }

        @Override
        public void onMessage(final int msgTypeId, final MutableDirectBuffer buffer, final int index, final int length)
        {
            for (int i = 0; i <= length - BitUtil.SIZE_OF_LONG; i += BitUtil.SIZE_OF_LONG)
            {
                blackhole.consume(buffer.getLong(index + i));
            }
            Blackhole.consumeCPU(consumerTokens);
            if (++messagesInBurst == burstLength)
            {
                messagesInBurst = 0;
                burstsConsumed.set(++burstsHandled);
            }
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    @Threads(1)
    public long test1Producer()
    {
        final RingBuffer ringBuffer = this.ringBuffer;
        final AtomicBuffer buffer = ringBuffer.buffer();
        final byte[] message = this.message;

        for (int i = 0; i < burstLength; i++)
        {
            int index;
            while ((index = ringBuffer.tryClaim(1, messageLength)) == RingBuffer.INSUFFICIENT_CAPACITY)
            {
                Thread.onSpinWait();
            }
            buffer.putBytes(index, message, 0, message.length);
            ringBuffer.commit(index);
        }

        final long expected = ++burstsPublished;
        while (burstsConsumed.get() < expected)
        {
            Thread.onSpinWait();
        }

        return expected;
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    @Threads(1)
    public long test1ProducerBatchPublish()
    {
        final RingBuffer ringBuffer = this.ringBuffer;
        final AtomicBuffer buffer = ringBuffer.buffer();
        final byte[] message = this.message;

        // Agrona has no batch publish. Its consumer polls each record's length header rather than a cursor, so every
        // record still pays for a tail store at claim and a header store at commit. The consumer can't read past an
        // uncommitted claim, so holding the first commit back publishes the burst at once, but unlike the Disruptor's
        // batch publish, this only delays when the consumer sees the burst and saves the producer nothing.
        int first = 0;
        for (int i = 0; i < burstLength; i++)
        {
            int index;
            while ((index = ringBuffer.tryClaim(1, messageLength)) == RingBuffer.INSUFFICIENT_CAPACITY)
            {
                Thread.onSpinWait();
            }
            buffer.putBytes(index, message, 0, message.length);
            if (i == 0)
            {
                first = index;
            }
            else
            {
                ringBuffer.commit(index);
            }
        }
        ringBuffer.commit(first);

        final long expected = ++burstsPublished;
        while (burstsConsumed.get() < expected)
        {
            Thread.onSpinWait();
        }

        return expected;
    }

    private static IdleStrategy createIdleStrategyByName(final String strategyName)
    {
        try
        {
            final Class<?> clazz = Class.forName("org.agrona.concurrent." + strategyName);
            return (IdleStrategy) clazz.getDeclaredConstructor().newInstance();
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Failed to create IdleStrategy: " + strategyName, e);
        }
    }

    private static Options options()
    {
        return new OptionsBuilder()
            .include(OneToOneRingBufferBurstBenchmark.class.getSimpleName())
            .shouldFailOnError(true)
            .resultFormat(ResultFormatType.JSON)
            .result(RESULT_FILE.toString())
            .build();
    }

    public static void main(final String[] args) throws RunnerException, IOException
    {
        Files.createDirectories(RESULT_FILE.toAbsolutePath().getParent());
        new Runner(options()).run();
    }
}
