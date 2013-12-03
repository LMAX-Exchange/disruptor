package com.lmax.disruptor.immutable;

import com.lmax.disruptor.EventHandler;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    public long counter;

    @Override
    public void onEvent(SimpleEvent arg0, long arg1, boolean arg2) throws Exception
    {
        counter += arg0.getCounter();
    }
}
