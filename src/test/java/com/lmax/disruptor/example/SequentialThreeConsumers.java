package com.lmax.disruptor.example;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
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

    private static final EventFactory<MyEvent> factory = () -> new MyEvent();

    private static final EventHandler<MyEvent> handler1 = (event, sequence, endOfBatch) -> event.b = event.a;

    private static final EventHandler<MyEvent> handler2 = (event, sequence, endOfBatch) -> event.c = event.b;

    private static final EventHandler<MyEvent> handler3 = (event, sequence, endOfBatch) -> event.d = event.c;

    public static void main(String[] args)
    {
        Disruptor<MyEvent> disruptor = new Disruptor<>(factory, 1024, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith(handler1).then(handler2).then(handler3);

        disruptor.start();
    }
}
