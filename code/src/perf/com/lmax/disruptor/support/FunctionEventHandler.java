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

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.util.PaddedLong;

import java.util.concurrent.CountDownLatch;

public final class FunctionEventHandler implements EventHandler<FunctionEvent>
{
    private final FunctionStep functionStep;
    private final PaddedLong stepThreeCounter = new PaddedLong();
    private long count;
    private CountDownLatch latch;

    public FunctionEventHandler(final FunctionStep functionStep)
    {
        this.functionStep = functionStep;
    }

    public long getStepThreeCounter()
    {
        return stepThreeCounter.get();
    }

    public void reset(final CountDownLatch latch, final long expectedCount)
    {
        stepThreeCounter.set(0L);
        this.latch = latch;
        count = expectedCount;
    }

    @Override
    public void onEvent(final FunctionEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
                event.setStepOneResult(event.getOperandOne() + event.getOperandTwo());
                break;

            case TWO:
                event.setStepTwoResult(event.getStepOneResult() + 3L);
                break;

            case THREE:
                if ((event.getStepTwoResult() & 4L) == 4L)
                {
                    stepThreeCounter.set(stepThreeCounter.get() + 1L);
                }
                break;
        }

        if (latch != null && count == sequence)
        {
            latch.countDown();
        }
    }
}
