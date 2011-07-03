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

public final class FizzBuzzHandler implements BatchHandler<FizzBuzzEntry>
{
    private final FizzBuzzStep fizzBuzzStep;
    private long fizzBuzzCounter = 0L;

    public FizzBuzzHandler(final FizzBuzzStep fizzBuzzStep)
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
    public void onAvailable(final FizzBuzzEntry entry) throws Exception
    {
        switch (fizzBuzzStep)
        {
            case FIZZ:
                entry.setFizz(0 == (entry.getValue() % 3));
                break;

            case BUZZ:
                entry.setBuzz(0 == (entry.getValue() % 5));
                break;

            case FIZZ_BUZZ:
                if (entry.isFizz() && entry.isBuzz())
                {
                    ++fizzBuzzCounter;
                }
                break;
        }
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }
}
