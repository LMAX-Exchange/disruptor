package com.lmax.disruptor;

import com.lmax.disruptor.support.TestEntry;
import com.lmax.disruptor.support.TestEventConsumer;
import org.junit.Assert;
import org.junit.Test;

public final class BusySpinSlotClaimerTest
{
    @Test
    public void shouldClaimFirstSlot()
    {
        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.FACTORY, 100);
        TestEventConsumer eventProcessor = new TestEventConsumer(0);

        SlotClaimer<TestEntry> slotClaimer = new BusySpinSlotClaimer<TestEntry>(0, ringBuffer, eventProcessor);

        TestEntry entry = slotClaimer.claimNext();

        Assert.assertEquals(0L, entry.getSequence());
    }

    @Test
	public void shouldClaimSequence() throws Exception
	{
    	int sequence = 15;

        RingBuffer<TestEntry> ringBuffer = new RingBuffer<TestEntry>(TestEntry.FACTORY, 100);
        TestEventConsumer eventProcessor = new TestEventConsumer(0);

        SlotClaimer<TestEntry> slotClaimer = new BusySpinSlotClaimer<TestEntry>(20, ringBuffer, eventProcessor);

		TestEntry entry = slotClaimer.claimSequence(sequence);

        Assert.assertEquals(sequence, entry.getSequence());
	}
}
