package com.lmax.disruptor.examples.longevent.legacy;

// tag::example[]
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.examples.longevent.LongEvent;

import java.nio.ByteBuffer;

public class LongEventProducer
{
    private final RingBuffer<LongEvent> ringBuffer;

    public LongEventProducer(RingBuffer<LongEvent> ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    public void onData(ByteBuffer bb)
    {
        long sequence = ringBuffer.next(); // <1>
        try
        {
            LongEvent event = ringBuffer.get(sequence); // <2>
            event.set(bb.getLong(0));  // <3>
        }
        finally
        {
            ringBuffer.publish(sequence);
        }
    }
}
// end::example[]