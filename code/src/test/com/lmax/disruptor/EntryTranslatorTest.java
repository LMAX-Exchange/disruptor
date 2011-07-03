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

import com.lmax.disruptor.support.StubEntry;
import org.junit.Assert;
import org.junit.Test;

public final class EntryTranslatorTest
{
    private static final String TEST_VALUE = "Wibble";

    @Test
    public void shouldTranslateOtherDataIntoAnEntry()
    {
        StubEntry entry = StubEntry.ENTRY_FACTORY.create();
        EntryTranslator<StubEntry> entryTranslator = new ExampleEntryTranslator(TEST_VALUE);

        entry = entryTranslator.translateTo(entry);

        Assert.assertEquals(TEST_VALUE, entry.getTestString());
    }

    public static final class ExampleEntryTranslator
        implements EntryTranslator<StubEntry>
    {
        private final String testValue;

        public ExampleEntryTranslator(final String testValue)
        {
            this.testValue = testValue;
        }

        @Override
        public StubEntry translateTo(final StubEntry entry)
        {
            entry.setTestString(testValue);
            return entry;
        }
    }
}
