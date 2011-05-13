package com.lmax.disruptor;

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
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int RING_SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 50;

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, RING_SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);
    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final ValueAdditionHandler handler = new ValueAdditionHandler();
    private final BatchConsumer<ValueEntry> batchConsumer = new BatchConsumer<ValueEntry>(consumerBarrier, handler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(0, batchConsumer);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(RING_SIZE);
    private final ValueAdditionQueueConsumer queueConsumer = new ValueAdditionQueueConsumer(blockingQueue);

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
        Future future =  EXECUTOR.submit(queueConsumer);

        long value = 0L;
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
            value += i;
        }

        final long expectedSequence = ITERATIONS - 1;
        while (queueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

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

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        batchConsumer.halt();

        Assert.assertEquals(value, handler.getValue());

        return opsPerSecond;
    }

    public static final class ValueAdditionQueueConsumer implements Runnable
    {
        private volatile boolean running;
        private volatile long sequence;
        private long value;

        private final BlockingQueue<Long> blockingQueue;

        public ValueAdditionQueueConsumer(final BlockingQueue<Long> blockingQueue)
        {
            this.blockingQueue = blockingQueue;
        }

        public long getValue()
        {
            return value;
        }

        public void reset()
        {
            value = 0L;
        }

        public long getSequence()
        {
            return sequence;
        }

        public void halt()
        {
            running = false;
        }

        @Override
        public void run()
        {
            running = true;
            while (running)
            {
                try
                {
                    long value = blockingQueue.take().longValue();
                    this.value += value;
                    sequence = value;
                }
                catch (InterruptedException ex)
                {
                    break;
                }
            }
        }
    }

    public static final class ValueAdditionHandler implements BatchHandler<ValueEntry>
    {
        private long value;

        public long getValue()
        {
            return value;
        }

        public void reset()
        {
            value = 0L;
        }

        @Override
        public void onAvailable(final ValueEntry entry) throws Exception
        {
            value += entry.getValue();
        }

        @Override
        public void onEndOfBatch() throws Exception
        {
        }

        @Override
        public void onCompletion()
        {
        }
    }
}
