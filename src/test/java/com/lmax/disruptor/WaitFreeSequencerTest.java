package com.lmax.disruptor;

import org.junit.Test;

import static org.junit.Assert.fail;

public class WaitFreeSequencerTest
{
    @Test(expected = IllegalArgumentException.class)
    public void failToCreateIfReserveSizeLargerThanHalfOfBufferSize() throws Exception
    {
        int size = 64;
        int halfSize = size / 2;
        new WaitFreeSequencer(size, new BlockingWaitStrategy(), halfSize + 1);
    }

    @Test
    public void dontSupplyNextValueIfOnlyReserveIsAvailable() throws Exception
    {
        int bufferSize = 64;
        int reserveSize = bufferSize / 2;

        WaitFreeSequencer sequencer = new WaitFreeSequencer(bufferSize, new BusySpinWaitStrategy(), reserveSize);
        Sequence consumer = new Sequence();
        sequencer.addGatingSequences(consumer);

        for (int i = 0; i < bufferSize - reserveSize; i++)
        {
            long next = sequencer.tryNext();
            sequencer.publish(next);
        }

        try
        {
            sequencer.tryNext();
            fail("Should have no capacity remaining");
        }
        catch (InsufficientCapacityException e)
        {
            // No-op
        }
    }
}
