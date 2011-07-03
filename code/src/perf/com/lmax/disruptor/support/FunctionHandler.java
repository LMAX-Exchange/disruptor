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
}
