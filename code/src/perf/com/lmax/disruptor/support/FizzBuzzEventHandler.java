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

public final class FizzBuzzEventHandler implements EventHandler<FizzBuzzEvent>
{
    private final FizzBuzzStep fizzBuzzStep;
    private final PaddedLong fizzBuzzCounter = new PaddedLong();
    private long count;
    private CountDownLatch latch;

    public FizzBuzzEventHandler(final FizzBuzzStep fizzBuzzStep)
    {
        this.fizzBuzzStep = fizzBuzzStep;
    }

    public void reset(final CountDownLatch latch, final long expectedCount)
    {
        fizzBuzzCounter.set(0L);
        this.latch = latch;
        count = expectedCount;
    }

    public long getFizzBuzzCounter()
    {
        return fizzBuzzCounter.get();
    }

    @Override
    public void onEvent(final FizzBuzzEvent event, final long sequence, final boolean endOfBatch) throws Exception
    {
        switch (fizzBuzzStep)
        {
            case FIZZ:
                if (0 == (event.getValue() % 3))
                {
                    event.setFizz(true);
                }
                break;

            case BUZZ:
                if (0 == (event.getValue() % 5))
                {
                    event.setBuzz(true);
                }
                break;

            case FIZZ_BUZZ:
                if (event.isFizz() && event.isBuzz())
                {
                    fizzBuzzCounter.set(fizzBuzzCounter.get() + 1L);
                }
                break;
        }

        if (latch != null && count == sequence)
        {
            latch.countDown();
        }
    }
}
