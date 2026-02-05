/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Spins, then yields, then parks for producers waiting on capacity.
 */
public final class PhasedBackoffProducerWaitStrategy implements ProducerWaitStrategy
{
    private final int spinTries;
    private final int yieldTries;
    private final long parkPeriodNanos;

    public PhasedBackoffProducerWaitStrategy(
        final int spinTries,
        final int yieldTries,
        final long parkPeriod,
        final TimeUnit unit)
    {
        if (spinTries < 0)
        {
            throw new IllegalArgumentException("spinTries must be >= 0");
        }
        if (yieldTries < 0)
        {
            throw new IllegalArgumentException("yieldTries must be >= 0");
        }
        if (parkPeriod < 1L)
        {
            throw new IllegalArgumentException("parkPeriod must be >= 1");
        }
        this.spinTries = spinTries;
        this.yieldTries = yieldTries;
        this.parkPeriodNanos = unit.toNanos(parkPeriod);
    }

    public static PhasedBackoffProducerWaitStrategy withDefaults()
    {
        return new PhasedBackoffProducerWaitStrategy(100, 100, 1L, TimeUnit.NANOSECONDS);
    }

    @Override
    public int idle(final int idleCounter)
    {
        if (idleCounter < spinTries)
        {
            Thread.onSpinWait();
            return idleCounter + 1;
        }
        if (idleCounter < spinTries + yieldTries)
        {
            Thread.yield();
            return idleCounter + 1;
        }

        LockSupport.parkNanos(parkPeriodNanos);
        return idleCounter + 1;
    }
}
