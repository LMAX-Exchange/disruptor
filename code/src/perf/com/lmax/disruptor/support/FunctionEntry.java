package com.lmax.disruptor.support;

import com.lmax.disruptor.AbstractEntry;
import com.lmax.disruptor.EntryFactory;

public final class FunctionEntry extends AbstractEntry
{
    private long operandOne;
    private long operandTwo;
    private long stepOneResult;
    private long stepTwoResult;

    public long getOperandOne()
    {
        return operandOne;
    }

    public void setOperandOne(final long operandOne)
    {
        this.operandOne = operandOne;
    }

    public long getOperandTwo()
    {
        return operandTwo;
    }

    public void setOperandTwo(final long operandTwo)
    {
        this.operandTwo = operandTwo;
    }

    public long getStepOneResult()
    {
        return stepOneResult;
    }

    public void setStepOneResult(final long stepOneResult)
    {
        this.stepOneResult = stepOneResult;
    }

    public long getStepTwoResult()
    {
        return stepTwoResult;
    }

    public void setStepTwoResult(final long stepTwoResult)
    {
        this.stepTwoResult = stepTwoResult;
    }

    public final static EntryFactory<FunctionEntry> ENTRY_FACTORY = new EntryFactory<FunctionEntry>()
    {
        public FunctionEntry create()
        {
            return new FunctionEntry();
        }
    };
}
