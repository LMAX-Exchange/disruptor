package com.lmax.disruptor.dsl;

import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.WaitStrategy;

public interface SequencerFactory
{
    Sequencer newInstance(int bufferSize, WaitStrategy waitStrategy);
}
