package com.lmax.disruptor.examples.longevent.lambdas;

// tag::example[]
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.examples.longevent.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.nio.ByteBuffer;

public class LongEventMain {

    public static void main(String[] args) throws Exception {
        // <1>
        int bufferSize = 1024;
        // <2>
        Disruptor<LongEvent> // <2>
        disruptor = new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> System.out.println("Event: " + event));
        // <4>
        disruptor.start();
        // <5>
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
        ByteBuffer bb = ByteBuffer.allocate(8);
        for (long l = 0; true; l++) {
            bb.putLong(0, l);
            ringBuffer.publishEvent((event, sequence, buffer) -> event.set(buffer.getLong(0)), bb);
            Thread.sleep(1000);
        }
    }
}
// end::example[]
