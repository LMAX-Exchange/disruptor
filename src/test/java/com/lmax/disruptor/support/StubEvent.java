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
    public static final EventTranslatorTwoArg<StubEvent, Integer, String> TRANSLATOR = (event, sequence, arg0, arg1) ->
            {
                event.setValue(arg0);
                event.setTestString(arg1);
            };

    public StubEvent(final int i)
    {
        this.value = i;
    }

    public void copy(final StubEvent event)
    {
        value = event.value;
    }

    public int getValue()
    {
        return value;
    }

    public void setValue(final int value)
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

    public static final EventFactory<StubEvent> EVENT_FACTORY = () -> new StubEvent(-1);

    @Override
    public boolean equals(final Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        final StubEvent stubEvent = (StubEvent) o;

        if (value != stubEvent.value)
        {
            return false;
        }
        return testString != null ? testString.equals(stubEvent.testString) : stubEvent.testString == null;
    }

    @Override
    public int hashCode()
    {
        int result = value;
        result = 31 * result + (testString != null ? testString.hashCode() : 0);
        return result;
    }
}
