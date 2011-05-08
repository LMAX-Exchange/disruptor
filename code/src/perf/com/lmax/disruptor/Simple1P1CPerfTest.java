package com.lmax.disruptor;

import com.lmax.disruptor.support.PerfEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public final class Simple1P1CPerfTest
{
    private static final int RING_SIZE = 4096;
    private static final long ITERATIONS = 1000 * 1000 * 100;

    private final RingBuffer<PerfEntry> ringBuffer = new RingBuffer<PerfEntry>(PerfEntry.ENTRY_FACTORY, RING_SIZE,
                                                                               ClaimStrategy.Option.SINGLE_THREADED,
                                                                               WaitStrategy.Option.BUSY_SPIN);
    private final ConsumerBarrier<PerfEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final TestEntryHandler testEntryHandler = new TestEntryHandler();
    private final BatchEntryConsumer<PerfEntry> batchEntryConsumer = new BatchEntryConsumer<PerfEntry>(consumerBarrier, testEntryHandler);
    private final ProducerBarrier<PerfEntry> producerBarrier = ringBuffer.createProducerBarrier(1, batchEntryConsumer);

    private final BlockingQueue<Long> blockingQueue = new ArrayBlockingQueue<Long>(RING_SIZE);
    private final BlockingQueueConsumer blockingQueueConsumer = new BlockingQueueConsumer(blockingQueue);

    @Test
    public void shouldCompareDisruptorVsArrayBlockingQueue()
        throws Exception
    {
        long disruptorOpsPerSecond = 0L;
        long blockingQueueOpsPerSecond = 0L;
        for (int i = 0; i < 3; i++)
        {
            disruptorOpsPerSecond = runDisruptorPass();
            blockingQueueOpsPerSecond = runBlockingQueuePass();
        }

        System.out.println("Disruptor 1P1C opsPerSecond = " + disruptorOpsPerSecond);
        System.out.println("BlockingQueue 1P1C opsPerSecond = " + blockingQueueOpsPerSecond);

        Assert.assertTrue("Performance preserved", disruptorOpsPerSecond > blockingQueueOpsPerSecond);
    }

    private long runDisruptorPass() throws InterruptedException
    {
        testEntryHandler.reset();
        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        long value = 0L;
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            PerfEntry entry = producerBarrier.claimNext();
            entry.setValue(i);
            entry.commit();

            value += i;
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (producerBarrier.getConsumedSequence() < expectedSequence)
        {
            Thread.yield();
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        batchEntryConsumer.halt();
        thread.join();

        Assert.assertEquals(value, testEntryHandler.getValue());

        return opsPerSecond;
    }

    private long runBlockingQueuePass() throws InterruptedException
    {
        blockingQueueConsumer.reset();
        Thread thread = new Thread(blockingQueueConsumer);
        thread.start();

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
            Thread.yield();
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        blockingQueueConsumer.halt();
        thread.interrupt();
        thread.join();

        Assert.assertEquals(value, blockingQueueConsumer.getValue());

        return opsPerSecond;
    }

    public static final class TestEntryHandler
        implements BatchEntryHandler<PerfEntry>
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
        public void onAvailable(final PerfEntry entry) throws Exception
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
        private volatile boolean running = true;
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

            while (running && !Thread.currentThread().isInterrupted())
            {
                try
                {
                    long value = blockingQueue.take().longValue();
                    this.value += value;
                    sequence = value;
                }
                catch (Exception ex)
                {
                    // fall through
                }
            }

        }
    }
}
