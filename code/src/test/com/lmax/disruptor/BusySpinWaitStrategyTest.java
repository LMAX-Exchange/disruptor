package com.lmax.disruptor;

import static com.lmax.disruptor.WaitStrategyTestUtil.assertWaitForWithDelayOf;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class BusySpinWaitStrategyTest
{
    
    @Test
    public void shouldWaitForValue() throws Exception
    {
        assertWaitForWithDelayOf(50, new BusySpinWaitStrategy());
    }

    
    @Test
    public void shouldWaitForValueWithTimeout() throws Exception
    {
        WaitStrategyTestUtil.assertWaitForWithTimeout(50, new BusySpinWaitStrategy());
    }
    
    @Test
    public void shouldTimeoutWhileWaiting() throws Exception
    {
        WaitStrategy strategy = new BusySpinWaitStrategy();
        assertThat(strategy.waitFor(0, new Sequence(-1), new DummySequenceBarrier(), 5, MILLISECONDS), is(-1L));
    }

}
