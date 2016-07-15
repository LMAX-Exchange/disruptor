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

    private static EventFactory<MyEvent> FACTORY = new EventFactory<MyEvent>()
    {
        @Override
        public MyEvent newInstance()
        {
            return new MyEvent();
        }
    };

    private static EventHandler<MyEvent> HANDLER_1 = new EventHandler<MyEvent>()
    {
        @Override
        public void onEvent(MyEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            event.b = event.a;
        }
    };

    private static EventHandler<MyEvent> HANDLER_2 = new EventHandler<MyEvent>()
    {
        @Override
        public void onEvent(MyEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            event.c = event.b;
        }
    };

    private static EventHandler<MyEvent> HANDLER_3 = new EventHandler<MyEvent>()
    {
        @Override
        public void onEvent(MyEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            event.d = event.c;
        }
    };

    public static void main(String[] args)
    {
        Disruptor<MyEvent> disruptor = new Disruptor<MyEvent>(FACTORY, 1024, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith(HANDLER_1).then(HANDLER_2).then(HANDLER_3);

        disruptor.start();
    }
}
