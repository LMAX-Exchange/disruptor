package com.lmax.disruptor.examples.longevent;

// tag::example[]
public class LongEvent {

    private long value;

    public void set(long value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "LongEvent{" + "value=" + value + '}';
    }
}
// end::example[]
