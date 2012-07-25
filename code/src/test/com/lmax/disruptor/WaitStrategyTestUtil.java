package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WaitStrategyTestUtil
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    static void assertWaitForWithDelayOf(long sleepTimeMillis, WaitStrategy waitStrategy)
            throws InterruptedException, BrokenBarrierException, AlertException
    {
        SequenceUpdater sequenceUpdater = new SequenceUpdater(sleepTimeMillis, waitStrategy);
        EXECUTOR.execute(sequenceUpdater);
        sequenceUpdater.waitForStartup();
        long sequence = waitStrategy.waitFor(0, sequenceUpdater.sequence, new DummySequenceBarrier());
        
        assertThat(sequence, is(0L));
    }

    public static void assertWaitForWithTimeout(long sleepTimeMillis, WaitStrategy waitStrategy)
            throws InterruptedException, BrokenBarrierException, AlertException
    {
        SequenceUpdater sequenceUpdater = new SequenceUpdater(sleepTimeMillis, waitStrategy);
        EXECUTOR.execute(sequenceUpdater);
        sequenceUpdater.waitForStartup();
        long sequence = waitStrategy.waitFor(0, sequenceUpdater.sequence, new DummySequenceBarrier(),
                                             sleepTimeMillis * 10, TimeUnit.MILLISECONDS);
        
        assertThat(sequence, is(0L));
    }
}
