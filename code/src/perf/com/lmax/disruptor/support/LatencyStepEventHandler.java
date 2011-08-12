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
import com.lmax.disruptor.collections.Histogram;

public final class LatencyStepEventHandler implements EventHandler<ValueEvent>
{
    private final FunctionStep functionStep;
    private final Histogram histogram;
    private final long nanoTimeCost;

    public LatencyStepEventHandler(final FunctionStep functionStep, final Histogram histogram, final long nanoTimeCost)
    {
        this.functionStep = functionStep;
        this.histogram = histogram;
        this.nanoTimeCost = nanoTimeCost;
    }

    @Override
    public void onEvent(final ValueEvent event, final boolean endOfBatch) throws Exception
    {
        switch (functionStep)
        {
            case ONE:
            case TWO:
                break;

            case THREE:
                // each value is a timestamp of when it was put on the ring
                // calculate how long it took for the value to get to the end
                long duration = System.nanoTime() - event.getValue();
                // approximate time for a single processor
                duration /= 3;
                // adjust for nanoTime() calls
                duration -= nanoTimeCost;
                histogram.addObservation(duration);
                break;
        }
    }
}
