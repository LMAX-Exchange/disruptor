package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Burst benchmark for the Disruptor, for comparison with {@link OneToOneRingBufferBurstBenchmark}.
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
public class DisruptorBurstBenchmark
{
    static final int CONSUMER_CPU = 1;
    static final int PRODUCER_CPU = 3;
    static final int RING_BUFFER_SIZE = 1024 * 1024;
    static final Path RESULT_FILE = Path.of("build", "reports", "jmh", "DisruptorBurstBenchmark.json");
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

    private Disruptor<Message> disruptor;
    private Handler handler;
    private RingBuffer<Message> ringBuffer;
    private byte[] message;
    private long burstsPublished;

    @Setup
    public void setup(final Blackhole blackhole) throws InterruptedException
    {
        affinityLocks.add(AffinityLock.acquireLock(PRODUCER_CPU));

        handler = new Handler(affinityLocks, burstLength, consumerTokens, blackhole, burstsConsumed);
        message = new byte[messageLength];

        final int messageLength = this.messageLength;
        disruptor = new Disruptor<>(
            () -> new Message(messageLength),
            RING_BUFFER_SIZE,
            Thread::new,
            ProducerType.SINGLE,
            createWaitStrategyByName(waitStrategyClassName));

        disruptor.handleEventsWith(handler);
        ringBuffer = disruptor.start();

        handler.waitForStart();
    }

    @TearDown
    public void tearDown() throws Exception
    {
        affinityLocks.forEach(AffinityLock::release);
        disruptor.shutdown();
        handler.waitForShutdown();
    }

    public static class Handler implements EventHandler<Message>
    {
        private final List<AffinityLock> affinityLocks;
        private final int burstLength;
        private final long consumerTokens;
        private final Blackhole blackhole;
        private final Sequence burstsConsumed;
        private int eventsInBurst;
        private long burstsHandled;
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final CountDownLatch stopLatch = new CountDownLatch(1);

        Handler(
            final List<AffinityLock> affinityLocks,
            final int burstLength,
            final long consumerTokens,
            final Blackhole blackhole,
            final Sequence burstsConsumed)
        {
            this.affinityLocks = affinityLocks;
            this.burstLength = burstLength;
            this.consumerTokens = consumerTokens;
            this.blackhole = blackhole;
            this.burstsConsumed = burstsConsumed;
        }

        @Override
        public void onEvent(final Message event, final long sequence, final boolean endOfBatch)
        {
            final byte[] bytes = event.bytes;
            for (int i = 0; i <= bytes.length - Long.BYTES; i += Long.BYTES)
            {
                blackhole.consume((long) LONG_VIEW.get(bytes, i));
            }
            Blackhole.consumeCPU(consumerTokens);
            if (++eventsInBurst == burstLength)
            {
                eventsInBurst = 0;
                burstsConsumed.set(++burstsHandled);
            }
        }

        @Override
        public void onStart()
        {
            affinityLocks.add(AffinityLock.acquireLock(CONSUMER_CPU));
            startLatch.countDown();
        }

        @Override
        public void onShutdown()
        {
            stopLatch.countDown();
        }

        public void waitForStart() throws InterruptedException
        {
            startLatch.await();
        }

        public void waitForShutdown() throws InterruptedException
        {
            stopLatch.await();
        }
    }

    static class Message
    {
        final byte[] bytes;

        Message(final int length)
        {
            bytes = new byte[length];
        }
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.AverageTime})
    @Threads(1)
    public long test1Producer()
    {
        final RingBuffer<Message> ringBuffer = this.ringBuffer;
        final byte[] message = this.message;

        for (int i = 0; i < burstLength; i++)
        {
            final long sequence = ringBuffer.next();
            System.arraycopy(message, 0, ringBuffer.get(sequence).bytes, 0, message.length);
            ringBuffer.publish(sequence);
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
        final RingBuffer<Message> ringBuffer = this.ringBuffer;
        final byte[] message = this.message;

        final long hi = ringBuffer.next(burstLength);
        final long lo = hi - (burstLength - 1);
        for (long sequence = lo; sequence <= hi; sequence++)
        {
            System.arraycopy(message, 0, ringBuffer.get(sequence).bytes, 0, message.length);
        }
        ringBuffer.publish(lo, hi);

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
            .include(DisruptorBurstBenchmark.class.getSimpleName())
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
