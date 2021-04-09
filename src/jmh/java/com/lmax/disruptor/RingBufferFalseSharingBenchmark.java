package com.lmax.disruptor;

import com.lmax.disruptor.util.SimpleEvent;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/*
 * Based on false-sharing benchmark in open JDK
 * @see https://github.com/openjdk/jmh/blob/master/jmh-samples/src/main/java/org/openjdk/jmh/samples/JMHSample_22_FalseSharing.java
 *
 */

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(5)
public class RingBufferFalseSharingBenchmark
{
    /*
     * We take advantage of the inheritance trick used in RingBuffer
     * to create an object without the padding that occur after the fields.
     *
     * Java object layout using JDK15:
     * com.lmax.disruptor.RingBufferFalseSharingBenchmark$HalfPaddedRingBufferWithNoisyNeighbour object internals:
 OFFSET  SIZE                           TYPE DESCRIPTION                                        VALUE
      0    12                                (object header)                                    N/A
     12     1                           byte RingBufferPad.p77                                  N/A
     13     1                           byte RingBufferPad.p11                                  N/A
     14     1                           byte RingBufferPad.p12                                  N/A
     15     1                           byte RingBufferPad.p13                                  N/A
     16     1                           byte RingBufferPad.p14                                  N/A
     17     1                           byte RingBufferPad.p15                                  N/A
     18     1                           byte RingBufferPad.p16                                  N/A
     19     1                           byte RingBufferPad.p17                                  N/A
     20     1                           byte RingBufferPad.p20                                  N/A
     21     1                           byte RingBufferPad.p21                                  N/A
     22     1                           byte RingBufferPad.p22                                  N/A
     23     1                           byte RingBufferPad.p23                                  N/A
     24     1                           byte RingBufferPad.p24                                  N/A
     25     1                           byte RingBufferPad.p25                                  N/A
     26     1                           byte RingBufferPad.p26                                  N/A
     27     1                           byte RingBufferPad.p27                                  N/A
     28     1                           byte RingBufferPad.p30                                  N/A
     29     1                           byte RingBufferPad.p31                                  N/A
     30     1                           byte RingBufferPad.p32                                  N/A
     31     1                           byte RingBufferPad.p33                                  N/A
     32     1                           byte RingBufferPad.p34                                  N/A
     33     1                           byte RingBufferPad.p35                                  N/A
     34     1                           byte RingBufferPad.p36                                  N/A
     35     1                           byte RingBufferPad.p37                                  N/A
     36     1                           byte RingBufferPad.p40                                  N/A
     37     1                           byte RingBufferPad.p41                                  N/A
     38     1                           byte RingBufferPad.p42                                  N/A
     39     1                           byte RingBufferPad.p43                                  N/A
     40     1                           byte RingBufferPad.p44                                  N/A
     41     1                           byte RingBufferPad.p45                                  N/A
     42     1                           byte RingBufferPad.p46                                  N/A
     43     1                           byte RingBufferPad.p47                                  N/A
     44     1                           byte RingBufferPad.p50                                  N/A
     45     1                           byte RingBufferPad.p51                                  N/A
     46     1                           byte RingBufferPad.p52                                  N/A
     47     1                           byte RingBufferPad.p53                                  N/A
     48     1                           byte RingBufferPad.p54                                  N/A
     49     1                           byte RingBufferPad.p55                                  N/A
     50     1                           byte RingBufferPad.p56                                  N/A
     51     1                           byte RingBufferPad.p57                                  N/A
     52     1                           byte RingBufferPad.p60                                  N/A
     53     1                           byte RingBufferPad.p61                                  N/A
     54     1                           byte RingBufferPad.p62                                  N/A
     55     1                           byte RingBufferPad.p63                                  N/A
     56     1                           byte RingBufferPad.p64                                  N/A
     57     1                           byte RingBufferPad.p65                                  N/A
     58     1                           byte RingBufferPad.p66                                  N/A
     59     1                           byte RingBufferPad.p67                                  N/A
     60     1                           byte RingBufferPad.p70                                  N/A
     61     1                           byte RingBufferPad.p71                                  N/A
     62     1                           byte RingBufferPad.p72                                  N/A
     63     1                           byte RingBufferPad.p73                                  N/A
     64     1                           byte RingBufferPad.p74                                  N/A
     65     1                           byte RingBufferPad.p75                                  N/A
     66     1                           byte RingBufferPad.p76                                  N/A
     67     1                           byte RingBufferPad.p10                                  N/A
     68     4                            int RingBufferFields.bufferSize                        N/A
     72     8                           long RingBufferFields.indexMask                         N/A
     80     4             java.lang.Object[] RingBufferFields.entries                           N/A
     84     4   com.lmax.disruptor.Sequencer RingBufferFields.sequencer                         N/A
     88     4                            int HalfPaddedRingBufferWithNoisyNeighbour.writeOnly   N/A
     92     4                                (loss due to the next object alignment)
Instance size: 96 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total

     */
    @State(Scope.Group)
    public static class HalfPaddedRingBufferWithNoisyNeighbour extends RingBufferFields<SimpleEvent>
    {
        int writeOnly;

        public HalfPaddedRingBufferWithNoisyNeighbour()
        {
            super(SimpleEvent::new, new SingleProducerSequencer(16, new BusySpinWaitStrategy()));
        }
    }

    @Benchmark
    @Group("halfpadded")
    public int reader(final HalfPaddedRingBufferWithNoisyNeighbour s)
    {
        return s.bufferSize;
    }

    @Benchmark
    @Group("halfpadded")
    public void writer(final HalfPaddedRingBufferWithNoisyNeighbour s)
    {
        s.writeOnly++;
    }

    /*
     * A fully padded RingBuffer using longs
     */

    @State(Scope.Group)
    public static class PaddedRingBuffer extends RingBufferFields<SimpleEvent>
        {
            protected byte
                p10, p11, p12, p13, p14, p15, p16, p17,
                p20, p21, p22, p23, p24, p25, p26, p27,
                p30, p31, p32, p33, p34, p35, p36, p37,
                p40, p41, p42, p43, p44, p45, p46, p47,
                p50, p51, p52, p53, p54, p55, p56, p57,
                p60, p61, p62, p63, p64, p65, p66, p67,
                p70, p71, p72, p73, p74, p75, p76, p77;

        public PaddedRingBuffer()
        {
            super(SimpleEvent::new, new SingleProducerSequencer(16, new BusySpinWaitStrategy()));
        }
    }

    /* Java object layout using JDK15:
    com.lmax.disruptor.RingBufferFalseSharingBenchmark$PaddedRingBufferWithNoisyNeighbour object internals:
 OFFSET  SIZE                           TYPE DESCRIPTION                                    VALUE
      0    12                                (object header)                                N/A
     12     1                           byte RingBufferPad.p77                              N/A
     13     1                           byte RingBufferPad.p11                              N/A
     14     1                           byte RingBufferPad.p12                              N/A
     15     1                           byte RingBufferPad.p13                              N/A
     16     1                           byte RingBufferPad.p14                              N/A
     17     1                           byte RingBufferPad.p15                              N/A
     18     1                           byte RingBufferPad.p16                              N/A
     19     1                           byte RingBufferPad.p17                              N/A
     20     1                           byte RingBufferPad.p20                              N/A
     21     1                           byte RingBufferPad.p21                              N/A
     22     1                           byte RingBufferPad.p22                              N/A
     23     1                           byte RingBufferPad.p23                              N/A
     24     1                           byte RingBufferPad.p24                              N/A
     25     1                           byte RingBufferPad.p25                              N/A
     26     1                           byte RingBufferPad.p26                              N/A
     27     1                           byte RingBufferPad.p27                              N/A
     28     1                           byte RingBufferPad.p30                              N/A
     29     1                           byte RingBufferPad.p31                              N/A
     30     1                           byte RingBufferPad.p32                              N/A
     31     1                           byte RingBufferPad.p33                              N/A
     32     1                           byte RingBufferPad.p34                              N/A
     33     1                           byte RingBufferPad.p35                              N/A
     34     1                           byte RingBufferPad.p36                              N/A
     35     1                           byte RingBufferPad.p37                              N/A
     36     1                           byte RingBufferPad.p40                              N/A
     37     1                           byte RingBufferPad.p41                              N/A
     38     1                           byte RingBufferPad.p42                              N/A
     39     1                           byte RingBufferPad.p43                              N/A
     40     1                           byte RingBufferPad.p44                              N/A
     41     1                           byte RingBufferPad.p45                              N/A
     42     1                           byte RingBufferPad.p46                              N/A
     43     1                           byte RingBufferPad.p47                              N/A
     44     1                           byte RingBufferPad.p50                              N/A
     45     1                           byte RingBufferPad.p51                              N/A
     46     1                           byte RingBufferPad.p52                              N/A
     47     1                           byte RingBufferPad.p53                              N/A
     48     1                           byte RingBufferPad.p54                              N/A
     49     1                           byte RingBufferPad.p55                              N/A
     50     1                           byte RingBufferPad.p56                              N/A
     51     1                           byte RingBufferPad.p57                              N/A
     52     1                           byte RingBufferPad.p60                              N/A
     53     1                           byte RingBufferPad.p61                              N/A
     54     1                           byte RingBufferPad.p62                              N/A
     55     1                           byte RingBufferPad.p63                              N/A
     56     1                           byte RingBufferPad.p64                              N/A
     57     1                           byte RingBufferPad.p65                              N/A
     58     1                           byte RingBufferPad.p66                              N/A
     59     1                           byte RingBufferPad.p67                              N/A
     60     1                           byte RingBufferPad.p70                              N/A
     61     1                           byte RingBufferPad.p71                              N/A
     62     1                           byte RingBufferPad.p72                              N/A
     63     1                           byte RingBufferPad.p73                              N/A
     64     1                           byte RingBufferPad.p74                              N/A
     65     1                           byte RingBufferPad.p75                              N/A
     66     1                           byte RingBufferPad.p76                              N/A
     67     1                           byte RingBufferPad.p10                              N/A
     68     4                            int RingBufferFields.bufferSize                    N/A
     72     8                           long RingBufferFields.indexMask                     N/A
     80     4             java.lang.Object[] RingBufferFields.entries                       N/A
     84     4   com.lmax.disruptor.Sequencer RingBufferFields.sequencer                     N/A
     88     1                           byte PaddedRingBuffer.p77                           N/A
     89     1                           byte PaddedRingBuffer.p11                           N/A
     90     1                           byte PaddedRingBuffer.p12                           N/A
     91     1                           byte PaddedRingBuffer.p13                           N/A
     92     1                           byte PaddedRingBuffer.p14                           N/A
     93     1                           byte PaddedRingBuffer.p15                           N/A
     94     1                           byte PaddedRingBuffer.p16                           N/A
     95     1                           byte PaddedRingBuffer.p17                           N/A
     96     1                           byte PaddedRingBuffer.p20                           N/A
     97     1                           byte PaddedRingBuffer.p21                           N/A
     98     1                           byte PaddedRingBuffer.p22                           N/A
     99     1                           byte PaddedRingBuffer.p23                           N/A
    100     1                           byte PaddedRingBuffer.p24                           N/A
    101     1                           byte PaddedRingBuffer.p25                           N/A
    102     1                           byte PaddedRingBuffer.p26                           N/A
    103     1                           byte PaddedRingBuffer.p27                           N/A
    104     1                           byte PaddedRingBuffer.p30                           N/A
    105     1                           byte PaddedRingBuffer.p31                           N/A
    106     1                           byte PaddedRingBuffer.p32                           N/A
    107     1                           byte PaddedRingBuffer.p33                           N/A
    108     1                           byte PaddedRingBuffer.p34                           N/A
    109     1                           byte PaddedRingBuffer.p35                           N/A
    110     1                           byte PaddedRingBuffer.p36                           N/A
    111     1                           byte PaddedRingBuffer.p37                           N/A
    112     1                           byte PaddedRingBuffer.p40                           N/A
    113     1                           byte PaddedRingBuffer.p41                           N/A
    114     1                           byte PaddedRingBuffer.p42                           N/A
    115     1                           byte PaddedRingBuffer.p43                           N/A
    116     1                           byte PaddedRingBuffer.p44                           N/A
    117     1                           byte PaddedRingBuffer.p45                           N/A
    118     1                           byte PaddedRingBuffer.p46                           N/A
    119     1                           byte PaddedRingBuffer.p47                           N/A
    120     1                           byte PaddedRingBuffer.p50                           N/A
    121     1                           byte PaddedRingBuffer.p51                           N/A
    122     1                           byte PaddedRingBuffer.p52                           N/A
    123     1                           byte PaddedRingBuffer.p53                           N/A
    124     1                           byte PaddedRingBuffer.p54                           N/A
    125     1                           byte PaddedRingBuffer.p55                           N/A
    126     1                           byte PaddedRingBuffer.p56                           N/A
    127     1                           byte PaddedRingBuffer.p57                           N/A
    128     1                           byte PaddedRingBuffer.p60                           N/A
    129     1                           byte PaddedRingBuffer.p61                           N/A
    130     1                           byte PaddedRingBuffer.p62                           N/A
    131     1                           byte PaddedRingBuffer.p63                           N/A
    132     1                           byte PaddedRingBuffer.p64                           N/A
    133     1                           byte PaddedRingBuffer.p65                           N/A
    134     1                           byte PaddedRingBuffer.p66                           N/A
    135     1                           byte PaddedRingBuffer.p67                           N/A
    136     1                           byte PaddedRingBuffer.p70                           N/A
    137     1                           byte PaddedRingBuffer.p71                           N/A
    138     1                           byte PaddedRingBuffer.p72                           N/A
    139     1                           byte PaddedRingBuffer.p73                           N/A
    140     1                           byte PaddedRingBuffer.p74                           N/A
    141     1                           byte PaddedRingBuffer.p75                           N/A
    142     1                           byte PaddedRingBuffer.p76                           N/A
    143     1                           byte PaddedRingBuffer.p10                           N/A
    144     4                            int PaddedRingBufferWithNoisyNeighbour.writeOnly   N/A
    148     4                                (loss due to the next object alignment)
Instance size: 152 bytes
Space losses: 0 bytes internal + 4 bytes external = 4 bytes total
     */
    @State(Scope.Group)
    public static class PaddedRingBufferWithNoisyNeighbour extends PaddedRingBuffer
    {
        int writeOnly;
    }

    @Benchmark
    @Group("padded")
    public int reader(final PaddedRingBufferWithNoisyNeighbour s)
    {
        return s.bufferSize;
    }

    @Benchmark
    @Group("padded")
    public void writer(final PaddedRingBufferWithNoisyNeighbour s)
    {
        s.writeOnly++;
    }

    public static void main(final String[] args) throws RunnerException
    {
        Options opt = new OptionsBuilder()
            .include(RingBufferFalseSharingBenchmark.class.getSimpleName())
            .threads(Runtime.getRuntime().availableProcessors())
            .build();

        new Runner(opt).run();
    }

}