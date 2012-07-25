package com.lmax.disruptor;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

class SequenceUpdater implements Runnable
{
    public final Sequence sequence = new Sequence();
    private final CyclicBarrier barrier = new CyclicBarrier(2);
    private final long sleepTime;
    private WaitStrategy waitStrategy;
    
    public SequenceUpdater(long sleepTime, WaitStrategy waitStrategy)
    {
        this.sleepTime = sleepTime;
        this.waitStrategy = waitStrategy;
    }
    
    @Override
    public void run()
    {
        try
        {
            barrier.await();
            if (0 != sleepTime)
            {
                Thread.sleep(sleepTime);
            }
            sequence.incrementAndGet();
            waitStrategy.signalAllWhenBlocking();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    public void waitForStartup() throws InterruptedException, BrokenBarrierException
    {
        barrier.await();
    }
}