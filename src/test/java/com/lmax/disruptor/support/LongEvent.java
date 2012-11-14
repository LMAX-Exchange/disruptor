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

import com.lmax.disruptor.EventFactory;

public class LongEvent
{
    private long value;

    public void set(long value)
    {
        this.value = value;
    }

    public long get()
    {
        return value;
    }

    public static final EventFactory<LongEvent> FACTORY = new EventFactory<LongEvent>()
    {
        @Override
        public LongEvent newInstance()
        {
            return new LongEvent();
        }
    };
}
