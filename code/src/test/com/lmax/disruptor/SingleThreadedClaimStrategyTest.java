package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class SingleThreadedClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        ClaimStrategy claimStrategy = new SingleThreadedClaimStrategy();

        claimStrategy.setSequence(7L);

        Assert.assertEquals(7L, claimStrategy.getAndIncrement());
        Assert.assertEquals(8L, claimStrategy.getAndIncrement());
    }
}
