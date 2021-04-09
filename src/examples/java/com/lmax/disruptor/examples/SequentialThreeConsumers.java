package com.lmax.disruptor.examples;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class SequentialThreeConsumers
{
    private static class MyEvent
    {
        private Object a;
        private Object b;
        private Object c;
        private Object d;
    }

    public static void main(final String[] args)
    {
        Disruptor<MyEvent> disruptor = new Disruptor<>(MyEvent::new, 1024, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> event.b = event.a)
                .then((event, sequence, endOfBatch) -> event.c = event.b)
                .then((event, sequence, endOfBatch) -> event.d = event.c);

        disruptor.start();
    }
}
