package com.lmax.disruptor;

import com.lmax.disruptor.support.ValueEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 *             +-----------------------------+
 *             |                             |
 *             |                             v
 * +----+    +----+    +----+    +----+    +----+
 * | P1 |--->| PB |--->| RB |<---| CB |<---| C1 |
 * +----+    +----+    +----+    +----+    +----+
 *
 * P1 - Producer 1
 * PB - Producer Barrier
 * RB - Ring Buffer
 * CB - Consumer Barrier
 * C1 - Consumer 1
 *
 */
public final class Simple1P1CPerfTest
{
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int RING_SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 100;

    private final RingBuffer<ValueEntry> ringBuffer = new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, RING_SIZE,
                                                                                 ClaimStrategy.Option.SINGLE_THREADED,
                                                                                 WaitStrategy.Option.YIELDING);
    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final TestEntryHandler testEntryHandler = new TestEntryHandler();
    private final BatchEntryConsumer<ValueEntry> batchEntryConsumer = new BatchEntryConsumer<ValueEntry>(consumerBarrier, testEntryHandler);
    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(1, batchEntryConsumer);

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(RING_SIZE);
    private final BlockingQueueConsumer blockingQueueConsumer = new BlockingQueueConsumer(blockingQueue);

    @Test
    public void shouldCompareDisruptorVsArrayBlockingQueue()
        throws Exception
    {
        final int RUNS = 3;
        long disruptorOps = 0L;
        long queueOps = 0L;

        for (int i = 0; i < RUNS; i++)
        {
            queueOps = runBlockingQueuePass();
            disruptorOps = runDisruptorPass();

            System.out.format("OpsPerSecond run %d: BlockingQueue=%d, Disruptor=%d\n",
                              Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runDisruptorPass() throws InterruptedException
    {
        testEntryHandler.reset();
        EXECUTOR.submit(batchEntryConsumer);

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
        while (batchEntryConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        batchEntryConsumer.halt();

        Assert.assertEquals(value, testEntryHandler.getValue());

        return opsPerSecond;
    }

    private long runBlockingQueuePass() throws InterruptedException
    {
        blockingQueueConsumer.reset();
        Future future =  EXECUTOR.submit(blockingQueueConsumer);

        long value = 0L;
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            blockingQueue.put(Long.valueOf(i));
            value += i;
        }

        final long expectedSequence = ITERATIONS - 1;
        while (blockingQueueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        blockingQueueConsumer.halt();
        future.cancel(true);

        Assert.assertEquals(value, blockingQueueConsumer.getValue());

        return opsPerSecond;
    }

    public static final class TestEntryHandler
        implements BatchEntryHandler<ValueEntry>
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

    public static final class BlockingQueueConsumer implements Runnable
    {
        private volatile boolean running;
        private volatile long sequence;
        private long value;

        private final BlockingQueue<Long> blockingQueue;

        public BlockingQueueConsumer(final BlockingQueue<Long> blockingQueue)
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
