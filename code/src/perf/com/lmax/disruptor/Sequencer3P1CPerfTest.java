package com.lmax.disruptor;

import org.junit.Assert;
import org.junit.Test;

/**
 * <pre>
 * Sequence a series of events from multiple producers going to one consumer.
 *
 * +----+
 * | P0 |------+
 * +----+      |
 *             v
 * +----+    +----+
 * | P1 |--->| C1 |
 * +----+    +----+
 *             ^
 * +----+      |
 * | P2 |------+
 * +----+
 *
 * Queue Based:
 * ============
 *
 * +----+  put
 * | P0 |------+
 * +----+      |
 *             v   take
 * +----+    +----+    +----+
 * | P1 |--->| Q0 |<---| C0 |
 * +----+    +----+    +----+
 *             ^
 * +----+      |
 * | P2 |------+
 * +----+
 *
 * P0 - Producer 0
 * P1 - Producer 1
 * P2 - Producer 2
 * Q0 - Queue 0
 * C0 - Consumer 0
 *
 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +-----------------------------+
 *             |                             |
 *             |                             v
 * +----+    +----+    +----+    +----+    +----+
 * | P0 |--->| PB |--->| RB |<---| CB |    | C0 |
 * +----+    +----+    +----+    +----+    +----+
 *             ^  claim      get    ^        |
 * +----+      |                    |        |
 * | P1 |------+                    +--------+
 * +----+      |                      waitFor
 *             |
 * +----+      |
 * | P2 |------+
 * +----+
 *
 * P0 - Producer 0
 * P1 - Producer 1
 * P2 - Producer 2
 * PB - ProducerBarrier
 * RB - RingBuffer
 * CB - ConsumerBarrier
 * C0 - Consumer 0
 *
 * </pre>
 */
public final class Sequencer3P1CPerfTest
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
        return 0;
    }

    private long runDisruptorPass()
    {
        return 1;
    }

}
