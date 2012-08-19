package com.lmax.disruptor;

import static com.lmax.disruptor.WaitStrategyTestUtil.assertWaitForWithDelayOf;

import org.junit.Test;

public class BusySpinWaitStrategyTest
{
    
    @Test
    public void shouldWaitForValue() throws Exception
    {
        assertWaitForWithDelayOf(50, new BusySpinWaitStrategy());
    }
}
