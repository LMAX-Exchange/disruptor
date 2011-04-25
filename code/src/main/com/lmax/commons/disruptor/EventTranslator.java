package com.lmax.commons.disruptor;


public interface EventTranslator<T>
{
    T translateTo(final T entry);
}
