package com.lmax.disruptor.examples.objectevent;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;

@SuppressWarnings("unchecked")
public class Main
{
    private static final int BUFFER_SIZE = 1024;

    // tag::example[]
    public static void main(String[] args)
    {
        Disruptor<ObjectEvent<String>> disruptor = new Disruptor<>(
                () -> new ObjectEvent<>(), BUFFER_SIZE, DaemonThreadFactory.INSTANCE);

        disruptor
                .handleEventsWith(new ProcessingEventHandler())
                .then(new ClearingEventHandler());
    }
    // end::example[]

}
