package com.lmax.commons.disruptor;

import org.junit.Assert;
import org.junit.Test;

public final class SingleThreadedSequenceClaimStrategyTest
{
    @Test
    public void shouldSetThenIncrement()
    {
        SequenceClaimStrategy sequenceClaimStrategy = new SingleThreadedSequenceClaimStrategy();

        sequenceClaimStrategy.setSequence(7L);

        Assert.assertEquals(7L, sequenceClaimStrategy.getAndIncrement());
        Assert.assertEquals(8L, sequenceClaimStrategy.getAndIncrement());
    }
}
