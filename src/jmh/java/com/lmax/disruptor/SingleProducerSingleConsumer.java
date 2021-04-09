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

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Fork(1)
public class SingleProducerSingleConsumer
{
    private RingBuffer<SimpleEvent> ringBuffer;
    private Disruptor<SimpleEvent> disruptor;

    @Setup
    public void setup(final Blackhole bh)
    {
        disruptor = new Disruptor<>(SimpleEvent::new,
                Constants.RINGBUFFER_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy());

        disruptor.handleEventsWith(new SimpleEventHandler(bh));

        ringBuffer = disruptor.start();
    }

    @Benchmark
    public void producing()
    {
        long sequence = ringBuffer.next();
        SimpleEvent simpleEvent = ringBuffer.get(sequence);
        simpleEvent.setValue(0);
        ringBuffer.publish(sequence);
    }

    @TearDown
    public void tearDown()
    {
        disruptor.shutdown();
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
                .include(SingleProducerSingleConsumer.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
