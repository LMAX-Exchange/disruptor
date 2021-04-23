package com.lmax.disruptor.examples.objectevent;

// tag::example[]
import com.lmax.disruptor.EventHandler;

public class ClearingEventHandler<T> implements EventHandler<ObjectEvent<T>>
{
    public void onEvent(ObjectEvent<T> event, long sequence, boolean endOfBatch)
    {
        // Failing to call clear here will result in the
        // object associated with the event to live until
        // it is overwritten once the ring buffer has wrapped
        // around to the beginning.
        event.clear();
    }
}
// end::example[]