package com.lmax.disruptor.immutable;

import com.lmax.disruptor.EventFactory;

public class EventHolder
{

    public static final EventFactory<EventHolder> FACTORY = new EventFactory<EventHolder>()
    {
        @Override
        public EventHolder newInstance()
        {
            return new EventHolder();
        }
    };

    public SimpleEvent event;
}
