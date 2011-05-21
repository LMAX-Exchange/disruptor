package com.lmax.disruptor.support;

import com.lmax.disruptor.ProducerBarrier;

import java.util.concurrent.CyclicBarrier;

public final class ValueProducer implements Runnable
{
    private final CyclicBarrier cyclicBarrier;
    private final ProducerBarrier<ValueEntry> producerBarrier;
    private final long iterations;

    public ValueProducer(final CyclicBarrier cyclicBarrier, final ProducerBarrier<ValueEntry> producerBarrier, final long iterations)
    {
        this.cyclicBarrier = cyclicBarrier;
        this.producerBarrier = producerBarrier;
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
                ValueEntry entry = producerBarrier.claimNext();
                entry.setValue(i);
                producerBarrier.commit(entry);
            }
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }
}
