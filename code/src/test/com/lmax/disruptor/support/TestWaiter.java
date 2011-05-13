package com.lmax.disruptor.support;

import com.lmax.disruptor.ConsumerBarrier;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;

public final class TestWaiter implements Callable<List<StubEntry>>
{
    private final long toWaitForSequence;
    private final long initialSequence;
    private final CyclicBarrier cyclicBarrier;
    private final ConsumerBarrier<StubEntry> consumerBarrier;

    public TestWaiter(final CyclicBarrier cyclicBarrier,
                      final ConsumerBarrier<StubEntry> consumerBarrier,
                      final long initialSequence,
                      final long toWaitForSequence)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.initialSequence = initialSequence;
        this.toWaitForSequence = toWaitForSequence;
        this.consumerBarrier = consumerBarrier;
    }

    @Override
    public List<StubEntry> call() throws Exception
    {
        cyclicBarrier.await();
        consumerBarrier.waitFor(toWaitForSequence);

        final List<StubEntry> messages = new ArrayList<StubEntry>();
        for (long l = initialSequence; l <= toWaitForSequence; l++)
        {
            messages.add(consumerBarrier.getEntry(l));
        }

        return messages;
    }
}