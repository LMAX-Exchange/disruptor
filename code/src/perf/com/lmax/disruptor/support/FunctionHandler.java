package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;

public final class FunctionHandler implements BatchHandler<FunctionEntry>
{
    private final Function function;
    private long stepThreeCounter;

    public FunctionHandler(final Function function)
    {
        this.function = function;
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
        switch (function)
        {
            case STEP_ONE:
                entry.setStepOneResult(entry.getOperandOne() + entry.getOperandTwo());
                break;

            case STEP_TWO:
                entry.setStepTwoResult(entry.getStepOneResult() + 3L);
                break;

            case STEP_THREE:
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
