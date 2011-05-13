package com.lmax.disruptor;

import com.lmax.disruptor.support.ValueAdditionHandler;
import com.lmax.disruptor.support.ValueAdditionQueueConsumer;
import com.lmax.disruptor.support.ValueEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * UniCast a series of items between 1 producer and 1 consumer.
 *
 * +----+    +----+
 * | P0 |--->| C0 |
 * +----+    +----+
 *
 * Queue Based:
 * ============
 *
 *        put      take
 * +----+    +----+    +----+
 * | P0 |--->| Q0 |<---| C0 |
 * +----+    +----+    +----+
 *
 * P0 - Producer 0
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
 *                claim      get    ^        |
 *                                  |        |
 *                                  +--------+
 *                                    waitFor
 *
 * P0 - Producer 0
 * PB - ProducerBarrier
 * RB - RingBuffer
 * CB - ConsumerBarrier
 * C0 - Consumer 0
 *
 * </pre>
 */
public final class UniCast1P1CPerfTest
{
    private static final int SIZE = 8192;
    private static final long ITERATIONS = 1000L * 1000L * 50L;
    private final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final ValueAdditionQueueConsumer queueConsumer = new ValueAdditionQueueConsumer(blockingQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.BUSY_SPIN);
    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final ValueAdditionHandler handler = new ValueAdditionHandler();
    private final BatchConsumer<ValueEntry> batchConsumer = new BatchConsumer<ValueEntry>(consumerBarrier, handler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(0, batchConsumer);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldCompareDisruptorVsQueue()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            queueOps = runQueuePass();
            disruptorOps = runDisruptorPass();

            System.out.format("%s OpsPerSecond run %d: BlockingQueue=%d, Disruptor=%d\n",
                              getClass().getSimpleName(), Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runQueuePass() throws InterruptedException
    {
        queueConsumer.reset();
        Future future = EXECUTOR.submit(queueConsumer);
        long value = 0L;
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
            value += i;
        }

        final long expectedSequence = ITERATIONS - 1L;
        while (queueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        queueConsumer.halt();
        future.cancel(true);

        Assert.assertEquals(value, queueConsumer.getValue());

        return opsPerSecond;
    }

    private long runDisruptorPass() throws InterruptedException
    {
        handler.reset();
        EXECUTOR.submit(batchConsumer);
        long value = 0L;
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEntry entry = producerBarrier.claimNext();
            entry.setValue(i);
            entry.commit();

            value += i;
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (batchConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        batchConsumer.halt();

        Assert.assertEquals(value, handler.getValue());

        return opsPerSecond;
    }
}
