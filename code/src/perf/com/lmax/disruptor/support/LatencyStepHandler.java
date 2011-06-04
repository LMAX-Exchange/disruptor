package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;
import com.lmax.disruptor.collections.Histogram;

public final class LatencyStepHandler implements BatchHandler<ValueEntry>
{
    private final FunctionStep functionStep;
    private final Histogram histogram;
    private final long nanoTimeCost;

    public LatencyStepHandler(final FunctionStep functionStep, final Histogram histogram, final long nanoTimeCost)
    {
        this.functionStep = functionStep;
        this.histogram = histogram;
        this.nanoTimeCost = nanoTimeCost;
    }

    @Override
    public void onAvailable(final ValueEntry entry) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
            case TWO:
                break;

            case THREE:
                long duration = System.nanoTime() - entry.getValue();
                duration /= 3;
                duration -= nanoTimeCost;
                histogram.addObservation(duration);
                break;
        }
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }

    @Override
    public void onCompletion()
    {
    }
}
