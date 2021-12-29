package com.lmax.disruptor.dsl;

import com.lmax.disruptor.sequence.Sequence;
import com.lmax.disruptor.barrier.SequenceBarrier;

import java.util.concurrent.ThreadFactory;

interface ConsumerInfo
{
    Sequence[] getSequences();

    SequenceBarrier getBarrier();

    boolean isEndOfChain();

    void start(ThreadFactory threadFactory);

    void halt();

    void markAsUsedInBarrier();

    boolean isRunning();
}
