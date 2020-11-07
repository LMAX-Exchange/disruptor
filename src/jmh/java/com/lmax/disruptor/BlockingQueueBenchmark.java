package com.lmax.disruptor;

import com.lmax.disruptor.util.Constants;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.SimpleEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
public class BlockingQueueBenchmark
{
    private AtomicLong eventsHandled;
    private BlockingQueue<SimpleEvent> arrayBlockingQueue;
    private volatile boolean consumerRunning;
    private SimpleEvent[] preMadeEvents;

    @Setup
    public void setup(final Blackhole bh)
    {
        eventsHandled = new AtomicLong();

        arrayBlockingQueue = new ArrayBlockingQueue<>(Constants.RINGBUFFER_SIZE);
        Thread eventHandler = DaemonThreadFactory.INSTANCE.newThread(() ->
        {
            while (consumerRunning)
            {
                SimpleEvent event = arrayBlockingQueue.poll();
                if (event != null)
                {
                    eventsHandled.incrementAndGet();
                    bh.consume(event);
                }
            }
        });
        consumerRunning = true;
        eventHandler.start();

        preMadeEvents = IntStream.range(0, Constants.ITERATIONS)
                .mapToObj(i ->
                {
                    SimpleEvent simpleEvent = new SimpleEvent();
                    simpleEvent.setValue(i);
                    return simpleEvent;
                }).toArray(SimpleEvent[]::new);
    }

    @Benchmark
    @OperationsPerInvocation(Constants.ITERATIONS)
    public void publishSimpleEvents() throws InterruptedException
    {
        eventsHandled.set(0);

        for (int i = 0; i < Constants.ITERATIONS; i++)
        {
            if (!arrayBlockingQueue.offer(preMadeEvents[i], 1, TimeUnit.NANOSECONDS))
            {
                throw new IllegalStateException("Queue full, benchmark should not experience backpressure");
            }
        }

        while (eventsHandled.get() != Constants.ITERATIONS)
        {
            Thread.yield();
        }
    }

    @TearDown
    public void tearDown()
    {
        consumerRunning = false;
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(BlockingQueueBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
