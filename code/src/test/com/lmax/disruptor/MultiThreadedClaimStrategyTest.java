package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class MultiThreadedClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        ClaimStrategy claimStrategy = new MultiThreadedClaimStrategy();

        claimStrategy.setSequence(7L);

        Assert.assertEquals(7L, claimStrategy.getAndIncrement());
        Assert.assertEquals(8L, claimStrategy.getAndIncrement());
    }
}
