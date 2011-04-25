package com.lmax.disruptor;


public interface EventTranslator<T>
{
    T translateTo(final T entry);
}
