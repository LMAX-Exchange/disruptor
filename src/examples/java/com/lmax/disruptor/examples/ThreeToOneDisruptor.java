package com.lmax.disruptor.examples;


import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class ThreeToOneDisruptor
{
    public static class DataEvent
    {
        Object input;
        Object[] output;

        public DataEvent(final int size)
        {
            output = new Object[size];
        }

        public static final EventFactory<DataEvent> FACTORY = () -> new DataEvent(3);
    }

    public static class TransformingHandler implements EventHandler<DataEvent>
    {
        private final int outputIndex;

        public TransformingHandler(final int outputIndex)
        {
            this.outputIndex = outputIndex;
        }

        @Override
        public void onEvent(final DataEvent event, final long sequence, final boolean endOfBatch)
        {
            // Do Stuff.
            event.output[outputIndex] = doSomething(event.input);
        }

        private Object doSomething(final Object input)
        {
            // Do required transformation here....
            return input;
        }
    }

    public static class CollatingHandler implements EventHandler<DataEvent>
    {
        @Override
        public void onEvent(final DataEvent event, final long sequence, final boolean endOfBatch)
        {
            collate(event.output);
        }

        private void collate(final Object[] output)
        {
            // Do required collation here....
        }
    }

    public static void main(final String[] args)
    {
        Disruptor<DataEvent> disruptor = new Disruptor<>(
                DataEvent.FACTORY, 1024, DaemonThreadFactory.INSTANCE);

        TransformingHandler handler1 = new TransformingHandler(0);
        TransformingHandler handler2 = new TransformingHandler(1);
        TransformingHandler handler3 = new TransformingHandler(2);
        CollatingHandler collator = new CollatingHandler();

        disruptor.handleEventsWith(handler1, handler2, handler3).then(collator);

        disruptor.start();
    }
}
