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
