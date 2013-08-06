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

import java.util.concurrent.CountDownLatch;


import com.lmax.disruptor.EventHandler;

public final class LatencyStepEventHandler implements EventHandler<ValueEvent>
{
    private final FunctionStep functionStep;
    private long count;
    private CountDownLatch latch;
    private long value;

    public LatencyStepEventHandler(final FunctionStep functionStep)
    {
        this.functionStep = functionStep;
    }

    public void reset(final CountDownLatch latch, final long expectedCount)
    {
        this.latch = latch;
        count = expectedCount;
    }

    public long getValue()
    {
        return value;
    }

    @Override
    public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
            case TWO:
                break;

            case THREE:

            value = event.getValue();

                break;
        }

        if (latch != null && count == sequence)
        {
            latch.countDown();
        }
    }
}
