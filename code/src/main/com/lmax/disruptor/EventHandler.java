package com.lmax.disruptor;



public interface EventHandler<T extends Entry>
{
    void onEvent(T entry) throws Exception;
    void onEndOfBatch() throws Exception;
    void onCompletion();
}
