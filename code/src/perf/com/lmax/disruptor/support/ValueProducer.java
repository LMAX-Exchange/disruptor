package com.lmax.disruptor.support;

import com.lmax.disruptor.ProducerBarrier;

import java.util.concurrent.CyclicBarrier;

public final class ValueProducer implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final ProducerBarrier<ValueEntry> barrier;
    private final long iterations;

    public ValueProducer(final CyclicBarrier cyclicBarrier, final ProducerBarrier<ValueEntry> barrier, final long iterations)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.barrier = barrier;
        this.iterations = iterations;
    }

    @Override
    public void run()
    {
        try
        {
            cyclicBarrier.await();

            for (long i = 0; i < iterations; i++)
            {
                ValueEntry entry = barrier.claimNext();
                entry.setValue(i);
                entry.commit();
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
