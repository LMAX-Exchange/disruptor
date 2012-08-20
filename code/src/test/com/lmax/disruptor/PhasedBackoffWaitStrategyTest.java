package com.lmax.disruptor;

import static com.lmax.disruptor.WaitStrategyTestUtil.assertWaitForWithDelayOf;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.junit.Test;

public class PhasedBackoffWaitStrategyTest
{
    @Test
    public void shouldHandleImmediateSequenceChange() throws Exception
    {
        assertWaitForWithDelayOf(0, PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS));
        assertWaitForWithDelayOf(0, PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS));
    }
    
    @Test
    public void shouldHandleSequenceChangeWithOneMillisecondDelay() throws Exception
    {
        assertWaitForWithDelayOf(1, PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS));
        assertWaitForWithDelayOf(1, PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS));
    }
    
    @Test
    public void shouldHandleSequenceChangeWithTwoMillisecondDelay() throws Exception
    {
        assertWaitForWithDelayOf(2, PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS));
        assertWaitForWithDelayOf(2, PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS));
    }
    
    @Test
    public void shouldHandleSequenceChangeWithTenMillisecondDelay() throws Exception
    {
        assertWaitForWithDelayOf(10, PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS));
        assertWaitForWithDelayOf(10, PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS));
    }
}
