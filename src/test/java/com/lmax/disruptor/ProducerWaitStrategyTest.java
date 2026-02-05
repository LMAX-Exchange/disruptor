package com.lmax.disruptor;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProducerWaitStrategyTest
{
    @Test
    void parkNanosStrategyValidatesArguments()
    {
        assertThrows(IllegalArgumentException.class, () -> new ParkNanosProducerWaitStrategy(0L));
    }

    @Test
    void phasedBackoffStrategyValidatesArguments()
    {
        assertThrows(IllegalArgumentException.class, () ->
            new PhasedBackoffProducerWaitStrategy(-1, 0, 1L, TimeUnit.NANOSECONDS));
        assertThrows(IllegalArgumentException.class, () ->
            new PhasedBackoffProducerWaitStrategy(0, -1, 1L, TimeUnit.NANOSECONDS));
        assertThrows(IllegalArgumentException.class, () ->
            new PhasedBackoffProducerWaitStrategy(0, 0, 0L, TimeUnit.NANOSECONDS));
    }

    @Test
    void idleCounterIncrementsAndResetIsNoOp()
    {
        ProducerWaitStrategy strategy = new PhasedBackoffProducerWaitStrategy(1, 1, 1L, TimeUnit.NANOSECONDS);
        int idleCounter = 0;

        idleCounter = strategy.idle(idleCounter);
        assertEquals(1, idleCounter);

        idleCounter = strategy.idle(idleCounter);
        assertEquals(2, idleCounter);

        strategy.reset();
    }
}
