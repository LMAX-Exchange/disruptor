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

public final class FizzBuzzEventHandler implements EventHandler<FizzBuzzEvent>
{
    private final FizzBuzzStep fizzBuzzStep;
    private long fizzBuzzCounter = 0L;

    public FizzBuzzEventHandler(final FizzBuzzStep fizzBuzzStep)
    {
        this.fizzBuzzStep = fizzBuzzStep;
    }

    public void reset()
    {
        fizzBuzzCounter = 0L;
    }

    public long getFizzBuzzCounter()
    {
        return fizzBuzzCounter;
    }

    @Override
    public void onEvent(final FizzBuzzEvent event, final boolean endOfBatch) throws Exception
    {
        switch (fizzBuzzStep)
        {
            case FIZZ:
                event.setFizz(0 == (event.getValue() % 3));
                break;

            case BUZZ:
                event.setBuzz(0 == (event.getValue() % 5));
                break;

            case FIZZ_BUZZ:
                if (event.isFizz() && event.isBuzz())
                {
                    ++fizzBuzzCounter;
                }
                break;
        }
    }
}
