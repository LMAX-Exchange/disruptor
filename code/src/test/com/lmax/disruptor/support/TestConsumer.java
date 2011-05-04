package com.lmax.disruptor.support;

import com.lmax.disruptor.Barrier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

public final class TestConsumer implements Callable<List<StubEntry>>
{
    private final long toWaitForSequence;
    private final long initialSequence;
    private final CyclicBarrier cyclicBarrier;
    private final Barrier<StubEntry> barrier;

    public TestConsumer(final CyclicBarrier cyclicBarrier,
                        final Barrier<StubEntry> barrier,
                        final long initialSequence,
                        final long toWaitForSequence)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.initialSequence = initialSequence;
        this.toWaitForSequence = toWaitForSequence;
        this.barrier = barrier;
    }

    @Override
    public List<StubEntry> call() throws Exception
    {
        cyclicBarrier.await();
        barrier.waitFor(toWaitForSequence);

        final List<StubEntry> messages = new ArrayList<StubEntry>();
        for (long l = initialSequence; l <= toWaitForSequence; l++)
        {
            messages.add(barrier.getEntry(l));
        }

        return messages;
    }
}