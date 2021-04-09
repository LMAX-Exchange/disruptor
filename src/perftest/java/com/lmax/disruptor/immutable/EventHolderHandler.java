package com.lmax.disruptor.immutable;

import com.lmax.disruptor.EventHandler;

public class EventHolderHandler implements EventHandler<EventHolder>
{
    private final EventHandler<SimpleEvent> delegate;

    public EventHolderHandler(final EventHandler<SimpleEvent> delegate)
    {
        this.delegate = delegate;
    }

    @Override
    public void onEvent(final EventHolder holder, final long sequence, final boolean endOfBatch) throws Exception
    {
        delegate.onEvent(holder.event, sequence, endOfBatch);
        holder.event = null;
    }
}
