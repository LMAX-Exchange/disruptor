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
 * | P1 |--->| C1 |
 * +----+    +----+
 *
 * Queue Based:
 * ============
 *
 *        put      take
 * +----+    +----+    +----+
 * | P1 |--->| Q1 |<---| C1 |
 * +----+    +----+    +----+
 *
 * P1 - Producer 1
 * Q1 - Queue 1
 * C1 - Consumer 1

 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +-----------------------------+
 *             |                             |
 *             |                             v
 * +----+    +----+    +----+    +----+    +----+
 * | P1 |--->| PB |--->| RB |<---| CB |    | C1 |
 * +----+    +----+    +----+    +----+    +----+
 *                claim      get    ^        |
 *                                  |        |
 *                                  +--------+
 *                                    waitFor
 *
 * P1 - Producer 1
 * PB - Producer Barrier
 * RB - Ring Buffer
 * CB - Consumer Barrier
 * C1 - Consumer 1
 * </pre>
 */
public final class UniCast1P1CPerfTest
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int RING_SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 50;

    private final RingBuffer<ValueEntry> ringBuffer = new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, RING_SIZE,
                                                                                 ClaimStrategy.Option.SINGLE_THREADED,
                                                                                 WaitStrategy.Option.YIELDING);
    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final TestHandler testHandler = new TestHandler();
    private final BatchConsumer<ValueEntry> batchConsumer = new BatchConsumer<ValueEntry>(consumerBarrier, testHandler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(1, batchConsumer);

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(RING_SIZE);
    private final QueueConsumer queueConsumer = new QueueConsumer(blockingQueue);

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

    private long runDisruptorPass() throws InterruptedException
    {
        testHandler.reset();
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

        Assert.assertEquals(value, testHandler.getValue());

        return opsPerSecond;
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

    public static final class TestHandler implements BatchHandler<ValueEntry>
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

    public static final class QueueConsumer implements Runnable
    {
        private volatile boolean running;
        private volatile long sequence;
        private long value;

        private final BlockingQueue<Long> blockingQueue;

        public QueueConsumer(final BlockingQueue<Long> blockingQueue)
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
}
