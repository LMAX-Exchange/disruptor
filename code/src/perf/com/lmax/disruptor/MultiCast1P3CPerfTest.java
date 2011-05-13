package com.lmax.disruptor;

import com.lmax.disruptor.support.ValueEntry;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <pre>
 * MultiCast a series of items between 1 producer and 3 consumers.
 *
 *           +----+
 *    +----->| C1 |
 *    |      +----+
 *    |
 * +----+    +----+
 * | P1 |--->| C2 |
 * +----+    +----+
 *    |
 *    |      +----+
 *    +----->| C3 |
 *           +----+

 * Queue Based:
 * ============
 *                 take
 *   put     +----+    +----+
 *    +----->| Q1 |<---| C1 |
 *    |      +----+    +----+
 *    |
 * +----+    +----+    +----+
 * | P1 |--->| Q2 |<---| C2 |
 * +----+    +----+    +----+
 *    |
 *    |      +----+    +----+
 *    +----->| Q3 |<---| C3 |
 *           +----+    +----+
 *
 * P1 - Producer 1
 * Q1 - Queue 1
 * C1 - Consumer 1
 * Q2 - Queue 2
 * C2 - Consumer 2
 * Q3 - Queue 3
 * C3 - Consumer 3
 *
 * Disruptor:
 * ==========
 *                            track to prevent wrap
 *             +-----------------------------+---------+---------+
 *             |                             |         |         |
 *             |                             v         v         v
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 * | P1 |--->| PB |--->| RB |<---| CB |    | C1 |    | C2 |    | C3 |
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 *                claim      get    ^        |          |        |
 *                                  |        |          |        |
 *                                  +--------+----------+--------+
 *                                               waitFor
 *
 * P1 - Producer 1
 * PB - Producer Barrier
 * RB - Ring Buffer
 * CB - Consumer Barrier
 * C1 - Consumer 1
 * C2 - Consumer 2
 * C3 - Consumer 3
 * </pre>
 */
public final class MultiCast1P3CPerfTest
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int RING_SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 50;

    private final RingBuffer<ValueEntry> ringBuffer = new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, RING_SIZE,
                                                                                 ClaimStrategy.Option.SINGLE_THREADED,
                                                                                 WaitStrategy.Option.YIELDING);

    @Test
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
    }
}
