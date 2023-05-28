package com.lmax.disruptor.examples.objectevent;

// tag::example[]
import com.lmax.disruptor.EventHandler;

public class ClearingEventHandler<T> implements EventHandler<ObjectEvent<T>> {

    @Override
    public void onEvent(ObjectEvent<T> event, long sequence, boolean endOfBatch) {
        // <1>
        event.clear();
    }
}
// end::example[]
