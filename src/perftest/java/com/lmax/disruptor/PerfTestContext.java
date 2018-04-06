package com.lmax.disruptor;

public class PerfTestContext
{
    private long disruptorOps;
    private long batchesProcessedCount;
    private long iterations;

    public PerfTestContext()
    {
    }

    public long getDisruptorOps()
    {
        return disruptorOps;
    }

    public void setDisruptorOps(long disruptorOps)
    {
        this.disruptorOps = disruptorOps;
    }

    public long getBatchesProcessedCount()
    {
        return batchesProcessedCount;
    }

    public double getBatchPercent()
    {
        if (batchesProcessedCount == 0) return 0;
        return 1 - (double)batchesProcessedCount / iterations;
    }

    public double getAverageBatchSize()
    {
        if (batchesProcessedCount == 0) return -1;
        return (double)iterations / batchesProcessedCount;
    }

    public void setBatchData(long batchesProcessedCount, long iterations)
    {
        this.batchesProcessedCount = batchesProcessedCount;
        this.iterations = iterations;
    }
}
