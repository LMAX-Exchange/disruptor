package com.lmax.disruptor.examples.objectevent;

// tag::example[]
import com.lmax.disruptor.EventHandler;

public class ClearingEventHandler<T> implements EventHandler<ObjectEvent<T>>
{
    public void onEvent(ObjectEvent<T> event, long sequence, boolean endOfBatch)
    {
        event.clear(); // <1>
    }
}
// end::example[]