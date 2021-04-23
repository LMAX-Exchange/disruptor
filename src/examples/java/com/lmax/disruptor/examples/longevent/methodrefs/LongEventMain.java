package com.lmax.disruptor.examples.longevent.methodrefs;

// tag::example[]

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.examples.longevent.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

import java.nio.ByteBuffer;

public class LongEventMain
{
    public static void handleEvent(LongEvent event, long sequence, boolean endOfBatch)
    {
        System.out.println(event);
    }

    public static void translate(LongEvent event, long sequence, ByteBuffer buffer)
    {
        event.set(buffer.getLong(0));
    }

    public static void main(String[] args) throws Exception
    {
        int bufferSize = 1024;

        Disruptor<LongEvent> disruptor =
                new Disruptor<>(LongEvent::new, bufferSize, DaemonThreadFactory.INSTANCE);
        disruptor.handleEventsWith(LongEventMain::handleEvent);
        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();
        ByteBuffer bb = ByteBuffer.allocate(8);
        for (long l = 0; true; l++)
        {
            bb.putLong(0, l);
            ringBuffer.publishEvent(LongEventMain::translate, bb);
            Thread.sleep(1000);
        }
    }
}
// end::example[]