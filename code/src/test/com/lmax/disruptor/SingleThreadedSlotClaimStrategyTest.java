package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class SingleThreadedSlotClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        SlotClaimStrategy slotClaimStrategy = new SingleThreadedSlotClaimStrategy();

        slotClaimStrategy.setSequence(7L);

        Assert.assertEquals(7L, slotClaimStrategy.getAndIncrement());
        Assert.assertEquals(8L, slotClaimStrategy.getAndIncrement());
    }
}
