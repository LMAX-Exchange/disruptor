package com.lmax.disruptor.examples.longevent.legacy;

// tag::example[]
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.examples.longevent.LongEvent;
import java.nio.ByteBuffer;

public class LongEventProducer {

    private final RingBuffer<LongEvent> ringBuffer;

    public LongEventProducer(RingBuffer<LongEvent> ringBuffer) {
        this.ringBuffer = ringBuffer;
    }

    public void onData(ByteBuffer bb) {
        // <1>
        long sequence = ringBuffer.next();
        try {
            // <2>
            LongEvent event = ringBuffer.get(sequence);
            // <3>
            event.set(bb.getLong(0));
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
// end::example[]
