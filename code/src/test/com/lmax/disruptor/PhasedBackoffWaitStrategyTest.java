package com.lmax.disruptor;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public class PhasedBackoffWaitStrategyTest
{
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Mockery mockery = new Mockery();
    private SequenceBarrier barrier;
    
    @Before
    public void setUp()
    {
        barrier = mockery.mock(SequenceBarrier.class);
        mockery.checking(new Expectations()
        {
            {
                allowing(barrier);
            }
        });
    }
    
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
        PhasedBackoffWaitStrategy lockTimeoutSequence = PhasedBackoffWaitStrategy.withLock(1, 1, MILLISECONDS);
        assertThat(lockTimeoutSequence.waitFor(0, new Sequence(-1), barrier, 5, MILLISECONDS), is(-1L));
        
        PhasedBackoffWaitStrategy sleepTimeSequence = PhasedBackoffWaitStrategy.withSleep(1, 1, MILLISECONDS);
        assertThat(sleepTimeSequence.waitFor(0, new Sequence(-1), barrier, 5, MILLISECONDS), is(-1L));
    }

    private void assertWaitForWithDelayOf(int sleepTime, PhasedBackoffWaitStrategy waitStrategy) throws InterruptedException, BrokenBarrierException,
                                                        AlertException
    {        
        SequenceUpdater sequenceUpdater = new SequenceUpdater(sleepTime, waitStrategy);
        executor.execute(sequenceUpdater);
        sequenceUpdater.waitForStartup();
        long sequence = waitStrategy.waitFor(0, sequenceUpdater.sequence, barrier);
        
        assertThat(sequence, is(0L));
    }
    
    private static class SequenceUpdater implements Runnable
    {
        public final Sequence sequence = new Sequence();
        private final CyclicBarrier barrier = new CyclicBarrier(2);
        private final long sleepTime;
        private PhasedBackoffWaitStrategy waitStrategy;
        
        public SequenceUpdater(long sleepTime, PhasedBackoffWaitStrategy waitStrategy)
        {
            this.sleepTime = sleepTime;
            this.waitStrategy = waitStrategy;
        }
        
        @Override
        public void run()
        {
            try
            {
                barrier.await();
                if (0 != sleepTime)
                {
                    Thread.sleep(sleepTime);
                }
                sequence.incrementAndGet();
                waitStrategy.signalAllWhenBlocking();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        public void waitForStartup() throws InterruptedException, BrokenBarrierException
        {
            barrier.await();
        }
    }
}
