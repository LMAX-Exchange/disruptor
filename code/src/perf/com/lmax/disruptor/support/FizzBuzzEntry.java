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

public final class FizzBuzzEntry extends AbstractEntry
{
    private long value = 0;
    private boolean fizz = false;
    private boolean buzz = false;

    public void reset()
    {
        value = 0L;
        fizz = false;
        buzz = false;
    }

    public long getValue()
    {
        return value;
    }

    public void setValue(final long value)
    {
        this.value = value;
    }

    public boolean isFizz()
    {
        return fizz;
    }

    public void setFizz(final boolean fizz)
    {
        this.fizz = fizz;
    }

    public boolean isBuzz()
    {
        return buzz;
    }

    public void setBuzz(final boolean buzz)
    {
        this.buzz = buzz;
    }


    public final static EntryFactory<FizzBuzzEntry> ENTRY_FACTORY = new EntryFactory<FizzBuzzEntry>()
    {
        public FizzBuzzEntry create()
        {
            return new FizzBuzzEntry();
        }
    };
}
