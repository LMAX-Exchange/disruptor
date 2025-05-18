package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * author: vbondarchuk
 * date: 5/18/2025
 * time: 7:58 PM
 **/

public class TimeoutBlockingProducerWaitStrategyTest
{

    @Test
    public void shouldThrowExceptionWhenTimeout()
    {
        final long startedAt = System.nanoTime();
        final TimeoutBlockingProducerWaitStrategy waitStrategy = new TimeoutBlockingProducerWaitStrategy(0, TimeUnit.MILLISECONDS);

        assertThrows(RuntimeTimeoutException.class, () -> waitStrategy.await(startedAt));
    }

}
