package com.lmax.disruptor.dsl;

import com.lmax.disruptor.MultiProducerSequencer;
import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.SingleProducerSequencer;
import com.lmax.disruptor.WaitStrategy;

public enum ProducerType
{
    SINGLE
    {
        public Sequencer createSequencer(int bufferSize, final WaitStrategy waitStrategy)
        {
            return new SingleProducerSequencer(bufferSize, waitStrategy);
        }
    },
    MULTI
    {
        public Sequencer createSequencer(int bufferSize, final WaitStrategy waitStrategy)
        {
            return new MultiProducerSequencer(bufferSize, waitStrategy);
        }
    };
    
    public abstract Sequencer createSequencer(int bufferSize, final WaitStrategy waitStrategy);
}
