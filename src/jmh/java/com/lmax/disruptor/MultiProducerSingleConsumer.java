package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
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
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(1)
public class MultiProducerSingleConsumer
{
    private RingBuffer<SimpleEvent> ringBuffer;
    private Disruptor<SimpleEvent> disruptor;
    private static final int BIG_BUFFER = 1 << 22;

    @Setup
    public void setup(final Blackhole bh)
    {
        disruptor = new Disruptor<>(SimpleEvent::new,
                BIG_BUFFER,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                new BusySpinWaitStrategy());

        disruptor.handleEventsWith(new SimpleEventHandler(bh));

        ringBuffer = disruptor.start();
    }

    @Benchmark
    @Threads(4)
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
                .include(MultiProducerSingleConsumer.class.getSimpleName())
                .build();
        new Runner(opt).run();
    }
}
