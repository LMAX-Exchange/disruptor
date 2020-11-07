package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.Constants;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.SimpleEvent;
import com.lmax.disruptor.util.SimpleEventHandler;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
public class SimpleBenchmark
{
    private RingBuffer<SimpleEvent> ringBuffer;
    private Disruptor<SimpleEvent> disruptor;
    private AtomicLong eventsHandled;

    @Setup
    public void setup()
    {
        eventsHandled = new AtomicLong();

        disruptor = new Disruptor<>(SimpleEvent::new,
                Constants.RINGBUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy());

        disruptor.handleEventsWith(new SimpleEventHandler(eventsHandled));

        ringBuffer = disruptor.start();
    }

    @Benchmark
    @OperationsPerInvocation(Constants.ITERATIONS)
    public void publishSimpleEvents()
    {
        eventsHandled.set(0);

        for (int i = 0; i < Constants.ITERATIONS; i++)
        {
            long sequence = ringBuffer.next();
            SimpleEvent simpleEvent = ringBuffer.get(sequence);
            simpleEvent.setValue(i);
            ringBuffer.publish(sequence);
        }

        while (eventsHandled.get() != Constants.ITERATIONS)
        {
            Thread.yield();
        }
    }

    @TearDown
    public void tearDown()
    {
        disruptor.shutdown();
    }

    public static void main(String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(SimpleBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
