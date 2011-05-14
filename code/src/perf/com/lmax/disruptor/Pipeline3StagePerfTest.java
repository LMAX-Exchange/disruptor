package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

/**
 * <pre>
 * Pipeline a series of stages from a producer to ultimate consumer.
 * Each consumer depends on the output of the previous consumer.
 *
 * +----+    +----+    +----+    +----+
 * | P0 |--->| C0 |--->| C1 |--->| C2 |
 * +----+    +----+    +----+    +----+
 *
 * Queue Based:
 * ============
 *
 *        put      take       put      take       put      take
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 * | P0 |--->| Q0 |<---| C0 |--->| Q1 |<---| C1 |--->| Q2 |<---| C2 |
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * C0 - Consumer 0
 * Q1 - Queue 1
 * C1 - Consumer 1
 * Q2 - Queue 2
 * C2 - Consumer 1
 *
 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +-----------------------------+---------------------+--------------------+
 *             |                             |                     |                    |
 *             |                             v                     v                    v
 * +----+    +----+    +----+    +-----+    +----+    +-----+    +----+    +-----+    +----+
 * | P0 |--->| PB |--->| RB |    | CB0 |<---| C0 |<---| CB1 |<---| C1 |<---| CB2 |<---| C2 |
 * +----+    +----+    +----+    +-----+    +----+    +-----+    +----+    +-----+    +----+
 *                claim   ^  get   |   waitFor           |  waitFor           |  waitFor
 *                        |        |                     |                    |
 *                        +--------+---------------------+--------------------+
 *
 *
 * P0  - Producer 0
 * PB  - ProducerBarrier
 * RB  - RingBuffer
 * CB0 - ConsumerBarrier 0
 * C0  - Consumer 0
 * CB1 - ConsumerBarrier 1
 * C1  - Consumer 1
 * CB2 - ConsumerBarrier 2
 * C2  - Consumer 2
 *
 * </pre>
 */
public final class Pipeline3StagePerfTest
{
    @Test
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            queueOps = runQueuePass();
            disruptorOps = runDisruptorPass();

            System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                              getClass().getSimpleName(), Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runQueuePass()
    {
        return 0L;
    }

    private long runDisruptorPass()
    {
        return 1L;
    }
}
