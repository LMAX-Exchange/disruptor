package com.lmax.disruptor;

import com.lmax.disruptor.bytes.ByteRecordHandler;
import com.lmax.disruptor.bytes.ByteRecordProcessor;
import com.lmax.disruptor.bytes.ByteRingBuffer;
import net.openhft.affinity.AffinityLock;
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Burst benchmark for {@link ByteRingBuffer}, for comparison with {@link DisruptorBurstBenchmark} and
 * {@link OneToOneRingBufferBurstBenchmark}.
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
public class ByteRingBufferBurstBenchmark
{
    static final int CONSUMER_CPU = 1;
    static final int PRODUCER_CPU = 3;
    static final int RING_BUFFER_CAPACITY = DisruptorBurstBenchmark.RING_BUFFER_SIZE;
    static final int TYPE_ID = 0;
    static final Path RESULT_FILE = Path.of("build", "reports", "jmh", "ByteRingBufferBurstBenchmark.json");
    static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    @Param({"1", "32", "64", "128"})
    int burstLength;

    @Param({"8", "128", "256"})
    int messageLength;

    /** Work the consumer does per message, in {@link Blackhole#consumeCPU} tokens. */
    @Param({"0", "24", "48", "96"})
    long consumerTokens;

    @Param({"BusySpinWaitStrategy"})
    String waitStrategyClassName;

    private final List<AffinityLock> affinityLocks = new CopyOnWriteArrayList<>();
    private final Sequence burstsConsumed = new Sequence(0);

    private ByteRingBuffer ringBuffer;
    private ByteRecordProcessor processor;
    private Thread consumerThread;
    private byte[] message;
    private long burstsPublished;

    @Setup
    public void setup(final Blackhole blackhole)
    {
        affinityLocks.add(AffinityLock.acquireLock(PRODUCER_CPU));

        // The capacity must be a power of two, so this may hold slightly fewer records than RING_BUFFER_CAPACITY.
        final int recordLength = ByteRingBuffer.align(ByteRingBuffer.HEADER_LENGTH + messageLength);
        ringBuffer = new ByteRingBuffer(
            Integer.highestOneBit(RING_BUFFER_CAPACITY * recordLength), createWaitStrategyByName(waitStrategyClassName));
        message = new byte[messageLength];

        processor = new ByteRecordProcessor(
            ringBuffer, ringBuffer.newBarrier(), new Handler(burstLength, consumerTokens, blackhole, burstsConsumed));
        ringBuffer.addGatingSequences(processor.getSequence());
        consumerThread = new Thread(() ->
        {
            affinityLocks.add(AffinityLock.acquireLock(CONSUMER_CPU));
            processor.run();
        }, "consumer");
        consumerThread.start();
    }

    @TearDown
    public void tearDown() throws Exception
    {
        affinityLocks.forEach(AffinityLock::release);
        processor.halt();
        consumerThread.join();
    }

    public static class Handler implements ByteRecordHandler
    {
        private final int burstLength;
        private final long consumerTokens;
        private final Blackhole blackhole;
        private final Sequence burstsConsumed;
        private int recordsInBurst;
        private long burstsHandled;

        Handler(final int burstLength, final long consumerTokens, final Blackhole blackhole, final Sequence burstsConsumed)
        {
            this.burstLength = burstLength;
            this.consumerTokens = consumerTokens;
            this.blackhole = blackhole;
            this.burstsConsumed = burstsConsumed;
        }

        @Override
        public void onRecord(
            final byte[] buffer, final int offset, final int length, final int typeId, final long position,
            final boolean endOfBatch)
        {
            for (int i = 0; i <= length - Long.BYTES; i += Long.BYTES)
            {
                blackhole.consume((long) LONG_VIEW.get(buffer, offset + i));
            }
            Blackhole.consumeCPU(consumerTokens);
            if (++recordsInBurst == burstLength)
            {
                recordsInBurst = 0;
                burstsConsumed.set(++burstsHandled);
            }
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    @Threads(1)
    public long test1Producer()
    {
        final ByteRingBuffer ringBuffer = this.ringBuffer;
        final byte[] message = this.message;

        for (int i = 0; i < burstLength; i++)
        {
            int offset;
            while ((offset = ringBuffer.tryClaim(TYPE_ID, message.length)) == ByteRingBuffer.INSUFFICIENT_CAPACITY)
            {
                Thread.onSpinWait();
            }
            System.arraycopy(message, 0, ringBuffer.buffer(), offset, message.length);
            ringBuffer.publish();
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
        final ByteRingBuffer ringBuffer = this.ringBuffer;
        final byte[] message = this.message;

        for (int i = 0; i < burstLength; i++)
        {
            int offset;
            while ((offset = ringBuffer.tryClaim(TYPE_ID, message.length)) == ByteRingBuffer.INSUFFICIENT_CAPACITY)
            {
                Thread.onSpinWait();
            }
            System.arraycopy(message, 0, ringBuffer.buffer(), offset, message.length);
        }
        ringBuffer.publish();

        final long expected = ++burstsPublished;
        while (burstsConsumed.get() < expected)
        {
            Thread.onSpinWait();
        }

        return expected;
    }

    private static WaitStrategy createWaitStrategyByName(final String strategyName)
    {
        try
        {
            final Class<?> clazz = Class.forName("com.lmax.disruptor." + strategyName);
            return (WaitStrategy) clazz.getDeclaredConstructor().newInstance();
        }
        catch (final Exception e)
        {
            throw new RuntimeException("Failed to create WaitStrategy: " + strategyName, e);
        }
    }

    private static Options options()
    {
        return new OptionsBuilder()
            .include(ByteRingBufferBurstBenchmark.class.getSimpleName())
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
