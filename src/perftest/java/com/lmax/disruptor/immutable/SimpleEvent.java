package com.lmax.disruptor.immutable;

public class SimpleEvent
{
    private final long id;
    private final long v1;
    private final long v2;
    private final long v3;

    public SimpleEvent(final long id, final long v1, final long v2, final long v3)
    {
        this.id = id;
        this.v1 = v1;
        this.v2 = v2;
        this.v3 = v3;
    }

    public long getCounter()
    {
        return v1;
    }

    @Override
    public String toString()
    {
        return "SimpleEvent [id=" + id + ", v1=" + v1 + ", v2=" + v2 + ", v3=" + v3 + "]";
    }
}