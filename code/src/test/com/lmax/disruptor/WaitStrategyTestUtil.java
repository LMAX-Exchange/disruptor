package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WaitStrategyTestUtil
{
    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();

    static void assertWaitForWithDelayOf(long sleepTimeMillis, WaitStrategy waitStrategy)
            throws InterruptedException, BrokenBarrierException, AlertException
    {
        SequenceUpdater sequenceUpdater = new SequenceUpdater(sleepTimeMillis, waitStrategy);
        EXECUTOR.execute(sequenceUpdater);
        sequenceUpdater.waitForStartup();
        Sequence cursor = new Sequence();
        long sequence = waitStrategy.waitFor(0, cursor, sequenceUpdater.sequence, new DummySequenceBarrier());
        
        assertThat(sequence, is(0L));
    }
}
