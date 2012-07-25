package com.lmax.disruptor;

import static com.lmax.disruptor.WaitStrategyTestUtil.assertWaitForWithDelayOf;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

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
    
    @Test
    public void shouldTimeoutWhileWaiting() throws Exception
    {
        DummySequenceBarrier barrier = new DummySequenceBarrier();
        
        PhasedBackoffWaitStrategy lockTimeoutSequence = PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS);
        assertThat(lockTimeoutSequence.waitFor(0, new Sequence(-1), barrier, 5, MILLISECONDS), is(-1L));
        
        PhasedBackoffWaitStrategy sleepTimeSequence = PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS);
        assertThat(sleepTimeSequence.waitFor(0, new Sequence(-1), barrier, 5, MILLISECONDS), is(-1L));
    }
}
