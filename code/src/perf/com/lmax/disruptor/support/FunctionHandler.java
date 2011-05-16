package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;

public final class FunctionHandler implements BatchHandler<FunctionEntry>
{
    private final FunctionStep functionStep;
    private long stepThreeCounter;

    public FunctionHandler(final FunctionStep functionStep)
    {
        this.functionStep = functionStep;
    }

    public long getStepThreeCounter()
    {
        return stepThreeCounter;
    }

    public void reset()
    {
        stepThreeCounter = 0L;
    }

    @Override
    public void onAvailable(final FunctionEntry entry) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
                entry.setStepOneResult(entry.getOperandOne() + entry.getOperandTwo());
                break;

            case TWO:
                entry.setStepTwoResult(entry.getStepOneResult() + 3L);
                break;

            case THREE:
                if ((entry.getStepTwoResult() & 4L) == 4L)
                {
                    stepThreeCounter++;
                }
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
