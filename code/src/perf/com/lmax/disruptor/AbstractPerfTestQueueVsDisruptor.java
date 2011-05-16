package com.lmax.disruptor;

import org.junit.Assert;

public abstract class AbstractPerfTestQueueVsDisruptor
{
    protected void testImplementations()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            System.gc();

            disruptorOps = runDisruptorPass(i);
            queueOps = runQueuePass(i);

            printResults(getClass().getSimpleName(), disruptorOps, queueOps, i);
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }


    public static void printResults(final String className, final long disruptorOps, final long queueOps, final int i)
    {
        System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                          className, Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
    }

    protected abstract long runQueuePass(int passNumber) throws Exception;

    protected abstract long runDisruptorPass(int passNumber) throws Exception;

    protected abstract void shouldCompareDisruptorVsQueues() throws Exception;
}
