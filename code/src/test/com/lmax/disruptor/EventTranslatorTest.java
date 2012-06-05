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
package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEvent;
import org.junit.Assert;
import org.junit.Test;

public final class EventTranslatorTest
{
    private static final String TEST_VALUE = "Wibble";

    @Test
    public void shouldTranslateOtherDataIntoAnEvent()
    {
        StubEvent event = StubEvent.EVENT_FACTORY.newInstance();
        EventTranslator<StubEvent> eventTranslator = new ExampleEventTranslator(TEST_VALUE);

        eventTranslator.translateTo(event, 0);

        Assert.assertEquals(TEST_VALUE, event.getTestString());
    }

    public static final class ExampleEventTranslator
        implements EventTranslator<StubEvent>
    {
        private final String testValue;

        public ExampleEventTranslator(final String testValue)
        {
            this.testValue = testValue;
        }

        @Override
        public void translateTo(final StubEvent event, long sequence)
        {
            event.setTestString(testValue);
        }
    }
}
