package com.lmax.disruptor;

import com.lmax.disruptor.support.DummySequenceBarrier;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LiteTimeoutBlockingWaitStrategyTest
{
    @Test
    public void shouldTimeoutWaitFor() throws Exception
    {
        final SequenceBarrier sequenceBarrier = new DummySequenceBarrier();

        long theTimeout = 500;
        LiteTimeoutBlockingWaitStrategy waitStrategy = new LiteTimeoutBlockingWaitStrategy(theTimeout, TimeUnit.MILLISECONDS);
        Sequence cursor = new Sequence(5);
        Sequence dependent = cursor;

        long t0 = System.currentTimeMillis();

        try
        {
            waitStrategy.waitFor(6, cursor, dependent, sequenceBarrier);
            fail("TimeoutException should have been thrown");
        }
        catch (TimeoutException e)
        {
        }

        long t1 = System.currentTimeMillis();

        long timeWaiting = t1 - t0;

        assertTrue(timeWaiting >= theTimeout);
    }
}
