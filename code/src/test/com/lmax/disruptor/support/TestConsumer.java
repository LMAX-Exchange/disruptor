package com.lmax.disruptor.support;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.Barrier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

public final class TestConsumer implements Callable<List<StubEntry>>
{
    private final RingBuffer<StubEntry> ringBuffer;
    private final long toWaitForSequence;
    private final long initialSequence;
    private final CyclicBarrier cyclicBarrier;
    private final Barrier barrier;

    public TestConsumer(final CyclicBarrier barrier,
                        final RingBuffer<StubEntry> ringBuffer,
                        final long initialSequence,
                        final long toWaitForSequence)
    {
        this.cyclicBarrier = barrier;
        this.ringBuffer = ringBuffer;
        this.initialSequence = initialSequence;
        this.toWaitForSequence = toWaitForSequence;
        this.barrier = ringBuffer.createBarrier();
    }

    @Override
    public List<StubEntry> call() throws Exception
    {
        cyclicBarrier.await();
        barrier.waitFor(toWaitForSequence);

        final List<StubEntry> messages = new ArrayList<StubEntry>();
        for (long l = initialSequence; l <= toWaitForSequence; l++)
        {
            messages.add(ringBuffer.getEntry(l));
        }

        return messages;
    }
}