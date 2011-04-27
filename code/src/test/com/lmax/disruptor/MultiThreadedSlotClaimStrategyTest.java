package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class MultiThreadedSlotClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        SlotClaimStrategy slotClaimStrategy = new MultiThreadedSlotClaimStrategy();

        slotClaimStrategy.setSequence(7L);

        Assert.assertEquals(7L, slotClaimStrategy.getAndIncrement());
        Assert.assertEquals(8L, slotClaimStrategy.getAndIncrement());
    }
}
