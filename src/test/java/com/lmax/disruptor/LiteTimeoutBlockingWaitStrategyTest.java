package com.lmax.disruptor;

import com.lmax.disruptor.support.DummySequenceBarrier;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LiteTimeoutBlockingWaitStrategyTest
{
    @Test
    public void shouldTimeoutWaitFor()
    {
        final SequenceBarrier sequenceBarrier = new DummySequenceBarrier();

        long theTimeout = 500;
        LiteTimeoutBlockingWaitStrategy waitStrategy = new LiteTimeoutBlockingWaitStrategy(theTimeout, TimeUnit.MILLISECONDS);
        Sequence cursor = new Sequence(5);

        long t0 = System.currentTimeMillis();

        assertThrows(TimeoutException.class, () -> waitStrategy.waitFor(6, cursor, cursor, sequenceBarrier));

        long t1 = System.currentTimeMillis();

        long timeWaiting = t1 - t0;

        assertTrue(timeWaiting >= theTimeout);
    }
}
