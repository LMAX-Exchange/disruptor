package com.lmax.commons.disruptor.support;

import com.lmax.commons.disruptor.RingBuffer;
import com.lmax.commons.disruptor.ThresholdBarrier;
import com.lmax.commons.disruptor.support.StubEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

public final class ReadingCallable implements Callable<List<StubEntry>>
{
    private final RingBuffer<StubEntry> ringBuffer;
    private final long toWaitFor;
    private final long initial;
    private final CyclicBarrier cb;
    private final ThresholdBarrier thresholdBarrier;

    public ReadingCallable(final CyclicBarrier barrier,
                           final RingBuffer<StubEntry> ringBuffer,
                           final long initial,
                           final long toWaitFor)
    {
        this.cb = barrier;
        this.ringBuffer = ringBuffer;
        this.initial = initial;
        this.toWaitFor = toWaitFor;
        thresholdBarrier = ringBuffer.createBarrier();
    }

    @Override
    public List<StubEntry> call() throws Exception
    {
        cb.await();
        final List<StubEntry> messages = new ArrayList<StubEntry>();
        thresholdBarrier.waitFor(toWaitFor);
        for (long l = initial; l <= toWaitFor; l++)
        {
            messages.add(ringBuffer.get(l));
        }
        return messages;
    }
}