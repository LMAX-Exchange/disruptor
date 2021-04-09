package com.lmax.disruptor.examples;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class Pipeliner
{
    public static void main(final String[] args)
    {
        Disruptor<PipelinerEvent> disruptor = new Disruptor<>(
                PipelinerEvent.FACTORY, 1024, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith(
            new ParallelHandler(0, 3),
            new ParallelHandler(1, 3),
            new ParallelHandler(2, 3)
        ).then(new JoiningHandler());

        RingBuffer<PipelinerEvent> ringBuffer = disruptor.start();

        for (int i = 0; i < 1000; i++)
        {
            long next = ringBuffer.next();
            try
            {
                PipelinerEvent pipelinerEvent = ringBuffer.get(next);
                pipelinerEvent.input = i;
            }
            finally
            {
                ringBuffer.publish(next);
            }
        }
    }

    private static class ParallelHandler implements EventHandler<PipelinerEvent>
    {
        private final int ordinal;
        private final int totalHandlers;

        ParallelHandler(final int ordinal, final int totalHandlers)
        {
            this.ordinal = ordinal;
            this.totalHandlers = totalHandlers;
        }

        @Override
        public void onEvent(final PipelinerEvent event, final long sequence, final boolean endOfBatch)
        {
            if (sequence % totalHandlers == ordinal)
            {
                event.result = Long.toString(event.input);
            }
        }
    }

    private static class JoiningHandler implements EventHandler<PipelinerEvent>
    {
        private long lastEvent = -1;

        @Override
        public void onEvent(final PipelinerEvent event, final long sequence, final boolean endOfBatch)
        {
            if (event.input != lastEvent + 1 || event.result == null)
            {
                System.out.println("Error: " + event);
            }

            lastEvent = event.input;
            event.result = null;
        }
    }

    private static class PipelinerEvent
    {
        long input;
        Object result;

        private static final EventFactory<PipelinerEvent> FACTORY = PipelinerEvent::new;

        @Override
        public String toString()
        {
            return "PipelinerEvent{" +
                "input=" + input +
                ", result=" + result +
                '}';
        }
    }
}
