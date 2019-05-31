package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class Main
{
    private static class MainEvent<T>
    {
        public T t;
    }

    public static void main(String[] args)
    {
        Disruptor<MainEvent<String>> d = new Disruptor<>(
            new EventFactory<MainEvent<String>>()
            {
                @Override
                public MainEvent<String> newInstance()
                {
                    return new MainEvent<>();
                }
            },
            1024,
            DaemonThreadFactory.INSTANCE
        );

        d.publishEvent(new EventTranslator<MainEvent<String>>()
        {
            @Override
            public void translateTo(MainEvent<String> event, long sequence)
            {
                event.t = "" + sequence;
            }
        });
    }
}
