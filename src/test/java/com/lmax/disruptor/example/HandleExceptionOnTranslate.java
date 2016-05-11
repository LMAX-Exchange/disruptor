package com.lmax.disruptor.example;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class HandleExceptionOnTranslate
{
    private static final int NO_VALUE_SPECIFIED = -1;

    private static class MyHandler implements EventHandler<LongEvent>
    {

        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            if (event.get() == NO_VALUE_SPECIFIED)
            {
                System.out.printf("Discarded%n");
            }
            else
            {
                System.out.printf("Processed: %s%n", event.get() == sequence);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        Disruptor<LongEvent> disruptor = new Disruptor<LongEvent>(LongEvent.FACTORY, 1024, DaemonThreadFactory.INSTANCE);

        disruptor.handleEventsWith(new MyHandler());

        disruptor.start();

        EventTranslator<LongEvent> t = new EventTranslator<LongEvent>()
        {
            @Override
            public void translateTo(LongEvent event, long sequence)
            {
                event.set(NO_VALUE_SPECIFIED);

                if (sequence % 3 == 0)
                {
                    throw new RuntimeException("Skipping");
                }

                event.set(sequence);
            }
        };

        for (int i = 0; i < 10; i++)
        {
            try
            {
                disruptor.publishEvent(t);
            }
            catch (RuntimeException e)
            {
                // Skipping
            }
        }

        Thread.sleep(5000);
    }
}
