package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class MultiThreadedSequenceClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        SequenceClaimStrategy sequenceClaimStrategy = new MultiThreadedSequenceClaimStrategy();

        sequenceClaimStrategy.setSequence(7L);

        Assert.assertEquals(7L, sequenceClaimStrategy.getAndIncrement());
        Assert.assertEquals(8L, sequenceClaimStrategy.getAndIncrement());
    }
}
