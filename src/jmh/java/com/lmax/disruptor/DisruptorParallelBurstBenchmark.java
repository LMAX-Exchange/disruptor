package com.lmax.disruptor;

import net.openhft.affinity.AffinityLock;
import org.openjdk.jmh.annotations.AuxCounters;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.SampleTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(value = 1, jvmArgsAppend = {
    "-Xmx1g",
    "-XX:+UseZGC",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+TrustFinalNonStaticFields"})
@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 5, time = 5)
public class DisruptorParallelBurstBenchmark
{
    static final int CONSUMER_CPU_ID = 8;
    static final int[] PRODUCER_CPU_IDS = {9, 10, 11};
    static final Path RESULT_DIR = Path.of("build", "reports", "jmh");
    static final VarHandle LONG_VIEW = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.nativeOrder());

    @Param({"1", "32", "64", "128"})
    long messagesPerProducer;

    /** Sized to hold as many messages as a contiguous array of this many MiB; see {@link #ringBufferSize}. */
    @Param({"128"})
    int rbSizeMb;

    @Param({"512"})
    int batchSizeReadMax;

    @Param({"1", "2", "3"})
    int producers;

    @Param({"16", "128", "256"})
    int messageLength;

    @Param({"0", "8", "32", "128"})
    long consumerTokens;

    @Param({"next", "tryNext"})
    String claim;

    private final List<AffinityLock> affinityLocks = new CopyOnWriteArrayList<>();
    private final List<Thread> producerThreads = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger startSignal = new AtomicInteger(0);

    private final Sequence consumed = new Sequence();

    private SumHandler messageHandler;
    private RingBuffer<Message> ringBuffer;
    private SequenceBarrier barrier;
    private long expectedSum;

    @Setup
    public void setup()
    {
        affinityLocks.add(AffinityLock.acquireLock(CONSUMER_CPU_ID));

        messageHandler = new SumHandler(consumerTokens);
        expectedSum = sumUpTo(messagesPerProducer) * producers;

        final int messageLength = this.messageLength;
        ringBuffer = RingBuffer.createMultiProducer(
            () -> new Message(messageLength), ringBufferSize(rbSizeMb, messageLength), new BusySpinWaitStrategy());
        ringBuffer.addGatingSequences(consumed);
        barrier = ringBuffer.newBarrier();

        for (int i = 0; i < producers; i++)
        {
            final Thread producer = createProducerThread(ringBuffer, "producer-" + i);
            producerThreads.add(producer);
            producer.start();
        }
    }

    /**
     * The largest power-of-two number of entries whose messages, laid end to end, fit in a contiguous array of
     * {@code rbSizeMb} MiB.
     */
    static int ringBufferSize(final int rbSizeMb, final int messageLength)
    {
        return Integer.highestOneBit(Math.toIntExact((1024L * 1024 * rbSizeMb) / messageLength));
    }

    private Thread createProducerThread(final RingBuffer<Message> rb, final String name)
    {
        final Thread producer = new Thread(() ->
        {
            affinityLocks.add(AffinityLock.acquireLock(PRODUCER_CPU_IDS));
            final boolean tryNext = "tryNext".equals(claim);
            int run = 0;
            while (true)
            {
                if (startSignal.get() <= run)
                {
                    if (!running.get())
                    {
                        return;
                    }
                    Thread.onSpinWait();
                    continue;
                }

                if (tryNext)
                {
                    tryPublishBurst(rb, messagesPerProducer);
                }
                else
                {
                    publishBurst(rb, messagesPerProducer);
                }
                run++;
            }
        }, name);

        producer.setDaemon(true);
        return producer;
    }

    /**
     * Publishes one burst. A method of its own, never inlined, so that it is compiled from its own profile: the
     * producer thread's loop never returns and is only compiled on-stack, where the spin on the start signal dominates
     * the profile and the claim and publish calls can be judged too cold to inline.
     */
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void publishBurst(final RingBuffer<Message> rb, final long messagesPerProducer)
    {
        for (int i = 1; i <= messagesPerProducer; i++)
        {
            publish(rb, rb.next(), i);
        }
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void tryPublishBurst(final RingBuffer<Message> rb, final long messagesPerProducer)
    {
        for (int i = 1; i <= messagesPerProducer; i++)
        {
            publish(rb, tryNext(rb), i);
        }
    }

    private static long tryNext(final RingBuffer<Message> rb)
    {
        while (true)
        {
            try
            {
                return rb.tryNext();
            }
            catch (final InsufficientCapacityException e)
            {
                Thread.onSpinWait();
            }
        }
    }

    private static void publish(final RingBuffer<Message> rb, final long sequence, final int value)
    {
        final byte[] bytes = rb.get(sequence).bytes;
        LONG_VIEW.set(bytes, 0, (long) value);
        Arrays.fill(bytes, Long.BYTES, bytes.length, (byte) 0);
        rb.publish(sequence);
    }

    @State(Scope.Thread)
    @AuxCounters(AuxCounters.Type.EVENTS)
    public static class Counters
    {
        /** Polls that hit {@code batchSizeReadMax}, so the consumer had more waiting. */
        public long maxBatchReads;
    }

    /**
     * Handles up to {@code batchSizeReadMax} published events per poll, then releases their slots to the producers.
     * Once it has consumed everything the barrier last reported, it waits on the barrier until the next sequence is
     * claimed, and polls again if that event is not yet published, as {@link BatchEventProcessor} does.
     */
    @Benchmark
    public long consumer(final Counters counters) throws Exception
    {
        startSignal.incrementAndGet();
        final SumHandler handler = this.messageHandler;
        handler.sum = 0;

        final RingBuffer<Message> ringBuffer = this.ringBuffer;
        final SequenceBarrier barrier = this.barrier;
        final Sequence consumed = this.consumed;
        final int batchSizeReadMax = this.batchSizeReadMax;

        long next = consumed.get() + 1;
        long available = next - 1;
        final long last = next - 1 + messagesPerProducer * producers;
        while (next <= last)
        {
            if (next > available)
            {
                available = barrier.waitFor(next);
                if (available < next)
                {
                    continue;
                }
            }

            final long end = Math.min(available, next + batchSizeReadMax - 1);
            for (long sequence = next; sequence <= end; sequence++)
            {
                handler.onEvent(ringBuffer.get(sequence));
            }
            consumed.set(end);
            if (end - next + 1 == batchSizeReadMax)
            {
                counters.maxBatchReads++;
            }
            next = end + 1;
        }
        return handler.sum;
    }

    @TearDown
    public void tearDown() throws InterruptedException
    {
        running.set(false);
        for (final Thread producer : producerThreads)
        {
            producer.join();
        }
        affinityLocks.forEach(AffinityLock::release);
        if (messageHandler.sum != expectedSum)
        {
            throw new IllegalStateException("The sum is not correct! " + messageHandler.sum + " != " + expectedSum);
        }
    }

    static long sumUpTo(final long n)
    {
        return n * (n + 1) / 2;
    }

    static final class Message
    {
        final byte[] bytes;

        Message(final int length)
        {
            bytes = new byte[length];
        }
    }

    /** Padding before {@link SumValue#sum}: superclass fields are laid out before subclass fields. */
    private abstract static class SumLhsPadding
    {
        protected byte
            p10, p11, p12, p13, p14, p15, p16, p17,
            p20, p21, p22, p23, p24, p25, p26, p27,
            p30, p31, p32, p33, p34, p35, p36, p37,
            p40, p41, p42, p43, p44, p45, p46, p47,
            p50, p51, p52, p53, p54, p55, p56, p57,
            p60, p61, p62, p63, p64, p65, p66, p67,
            p70, p71, p72, p73, p74, p75, p76, p77;
    }

    private abstract static class SumValue extends SumLhsPadding
    {
        protected long sum;
    }

    /**
     * The consumer's sum, which it adds to for every message, with no other object's fields in its cache line.
     * Without it, where the garbage collector happens to move the handler decides whether {@code sum} shares a line
     * with fields the producers read for every message, and a fork can run at either of two speeds. Padded like
     * {@link Sequence}: 56 bytes on each side of {@code sum}, so a 64-byte line holding it holds nothing else.
     */
    private abstract static class PaddedSum extends SumValue
    {
        protected byte
            p90, p91, p92, p93, p94, p95, p96, p97,
            p100, p101, p102, p103, p104, p105, p106, p107,
            p110, p111, p112, p113, p114, p115, p116, p117,
            p120, p121, p122, p123, p124, p125, p126, p127,
            p130, p131, p132, p133, p134, p135, p136, p137,
            p140, p141, p142, p143, p144, p145, p146, p147,
            p150, p151, p152, p153, p154, p155, p156, p157;
    }

    private static final class SumHandler extends PaddedSum
    {
        private final long consumerTokens;

        SumHandler(final long consumerTokens)
        {
            this.consumerTokens = consumerTokens;
        }

        void onEvent(final Message event)
        {
            final byte[] bytes = event.bytes;
            for (int i = 0; i < bytes.length; i += Long.BYTES)
            {
                sum += (long) LONG_VIEW.get(bytes, i);
            }
            Blackhole.consumeCPU(consumerTokens);
        }
    }

    public static void main(final String[] args) throws RunnerException, IOException
    {
        Files.createDirectories(RESULT_DIR.toAbsolutePath());

        new Runner(
            new OptionsBuilder()
                .include(DisruptorParallelBurstBenchmark.class.getSimpleName())
                .shouldFailOnError(true)
                .resultFormat(ResultFormatType.JSON)
                .result(RESULT_DIR.resolve("DisruptorParallelBurstBenchmark.json").toString())
                .build()
        ).run();
    }
}
