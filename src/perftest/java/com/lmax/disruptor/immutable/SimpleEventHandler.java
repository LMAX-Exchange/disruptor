package com.lmax.disruptor.immutable;

import com.lmax.disruptor.EventHandler;

public class SimpleEventHandler implements EventHandler<SimpleEvent>
{
    public long counter;

    @Override
    public void onEvent(final SimpleEvent arg0, final long arg1, final boolean arg2) throws Exception
    {
        counter += arg0.getCounter();
    }
}
