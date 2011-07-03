/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
