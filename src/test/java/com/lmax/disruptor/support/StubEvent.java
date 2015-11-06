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
import com.lmax.disruptor.EventTranslatorTwoArg;

public final class StubEvent
{
    private int value;
    private String testString;
    public static final EventTranslatorTwoArg<StubEvent, Integer, String> TRANSLATOR =
        new EventTranslatorTwoArg<StubEvent, Integer, String>()
        {
            @Override
            public void translateTo(StubEvent event, long sequence, Integer arg0, String arg1)
            {
                event.setValue(arg0);
                event.setTestString(arg1);
            }
        };

    public StubEvent(int i)
    {
        this.value = i;
    }

    public void copy(StubEvent event)
    {
        value = event.value;
    }

    public int getValue()
    {
        return value;
    }

    public void setValue(int value)
    {
        this.value = value;
    }

    public String getTestString()
    {
        return testString;
    }

    public void setTestString(final String testString)
    {
        this.testString = testString;
    }

    public static final EventFactory<StubEvent> EVENT_FACTORY = new EventFactory<StubEvent>()
    {
        public StubEvent newInstance()
        {
            return new StubEvent(-1);
        }
    };

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + value;
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        StubEvent other = (StubEvent) obj;

        return value == other.value;
    }
}
