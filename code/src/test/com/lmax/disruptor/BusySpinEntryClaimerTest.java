package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import com.lmax.disruptor.support.TestEntryConsumer;
import org.junit.Assert;
import org.junit.Test;

public final class BusySpinEntryClaimerTest
{
    @Test
    public void shouldClaimFirstEntry()
    {
        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.ENTRY_FACTORY, 100);
        TestEntryConsumer entryConsumer = new TestEntryConsumer(0);

        EntryClaimer<TestEntry> entryClaimer = new BusySpinEntryClaimer<TestEntry>(0, ringBuffer, entryConsumer);

        TestEntry entry = entryClaimer.claimNext();

        Assert.assertEquals(0L, entry.getSequence());
    }

    @Test
	public void shouldClaimSequence() throws Exception
	{
    	int sequence = 15;

        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.ENTRY_FACTORY, 100);
        TestEntryConsumer entryConsumer = new TestEntryConsumer(0);

        EntryClaimer<TestEntry> entryClaimer = new BusySpinEntryClaimer<TestEntry>(20, ringBuffer, entryConsumer);

		TestEntry entry = entryClaimer.claimSequence(sequence);

        Assert.assertEquals(sequence, entry.getSequence());
	}
}
