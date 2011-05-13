package com.lmax.disruptor;

import com.lmax.disruptor.support.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

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
    private static final int NUM_PRODUCERS = 3;
    private static final int SIZE = 8192;
    private static final long ITERATIONS = 1000L * 1000L * 50L;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_PRODUCERS + 1);
    private final CyclicBarrier cyclicBarrier = new CyclicBarrier(NUM_PRODUCERS + 1);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final ValueAdditionQueueConsumer queueConsumer = new ValueAdditionQueueConsumer(blockingQueue);
    private final ValueQueueProducer[] valueQueueProducers = new ValueQueueProducer[NUM_PRODUCERS];
    {
        valueQueueProducers[0] = new ValueQueueProducer(cyclicBarrier, blockingQueue, ITERATIONS);
        valueQueueProducers[1] = new ValueQueueProducer(cyclicBarrier, blockingQueue, ITERATIONS);
        valueQueueProducers[2] = new ValueQueueProducer(cyclicBarrier, blockingQueue, ITERATIONS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, SIZE,
                                   ClaimStrategy.Option.MULTI_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final ValueAdditionHandler handler = new ValueAdditionHandler();
    private final BatchConsumer<ValueEntry> batchConsumer = new BatchConsumer<ValueEntry>(consumerBarrier, handler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(0, batchConsumer);
    private final ValueProducer[] valueProducers = new ValueProducer[NUM_PRODUCERS];
    {
        valueProducers[0] = new ValueProducer(cyclicBarrier, producerBarrier, ITERATIONS);
        valueProducers[1] = new ValueProducer(cyclicBarrier, producerBarrier, ITERATIONS);
        valueProducers[2] = new ValueProducer(cyclicBarrier, producerBarrier, ITERATIONS);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

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
            disruptorOps = runDisruptorPass(i + 1);

            System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                              getClass().getSimpleName(), Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runQueuePass() throws Exception
    {
        Future[] futures = new Future[NUM_PRODUCERS];
        for (int i = 0; i < NUM_PRODUCERS; i++)
        {
            futures[i] = EXECUTOR.submit(valueQueueProducers[i]);
        }
        Future consumerFuture = EXECUTOR.submit(queueConsumer);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PRODUCERS; i++)
        {
            futures[i].get();
        }

        final long expectedSequence = (ITERATIONS * NUM_PRODUCERS) - 1L;
        while (expectedSequence > queueConsumer.getSequence())
        {
            // busy spin
        }

        long opsPerSecond = (NUM_PRODUCERS * ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchConsumer.halt();
        consumerFuture.cancel(true);

        return opsPerSecond;
    }

    private long runDisruptorPass(final int passNumber) throws Exception
    {
        Future[] futures = new Future[NUM_PRODUCERS];
        for (int i = 0; i < NUM_PRODUCERS; i++)
        {
            futures[i] = EXECUTOR.submit(valueProducers[i]);
        }
        EXECUTOR.submit(batchConsumer);

        long start = System.currentTimeMillis();
        cyclicBarrier.await();

        for (int i = 0; i < NUM_PRODUCERS; i++)
        {
            futures[i].get();
        }

        final long expectedSequence = (ITERATIONS * NUM_PRODUCERS * passNumber) - 1L;
        while (expectedSequence > batchConsumer.getSequence())
        {
            // busy spin
        }

        long opsPerSecond = (NUM_PRODUCERS * ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchConsumer.halt();

        return opsPerSecond;
    }
}
