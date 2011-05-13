package com.lmax.disruptor;

import com.lmax.disruptor.support.Operation;
import com.lmax.disruptor.support.ValueEntry;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 * MultiCast a series of items between 1 producer and 3 consumers.
 *
 *           +----+
 *    +----->| C0 |
 *    |      +----+
 *    |
 * +----+    +----+
 * | P0 |--->| C1 |
 * +----+    +----+
 *    |
 *    |      +----+
 *    +----->| C2 |
 *           +----+

 * Queue Based:
 * ============
 *                 take
 *   put     +----+    +----+
 *    +----->| Q0 |<---| C0 |
 *    |      +----+    +----+
 *    |
 * +----+    +----+    +----+
 * | P0 |--->| Q1 |<---| C1 |
 * +----+    +----+    +----+
 *    |
 *    |      +----+    +----+
 *    +----->| Q2 |<---| C2 |
 *           +----+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * Q1 - Queue 1
 * Q2 - Queue 2
 * C0 - Consumer 0
 * C1 - Consumer 1
 * C2 - Consumer 2
 *
 * Disruptor:
 * ==========
 *                            track to prevent wrap
 *             +-----------------------------+---------+---------+
 *             |                             |         |         |
 *             |                             v         v         v
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 * | P0 |--->| PB |--->| RB |<---| CB |    | C0 |    | C1 |    | C2 |
 * +----+    +----+    +----+    +----+    +----+    +----+    +----+
 *                claim      get    ^        |          |        |
 *                                  |        |          |        |
 *                                  +--------+----------+--------+
 *                                               waitFor
 *
 * P0 - Producer 0
 * PB - ProducerBarrier
 * RB - RingBuffer
 * CB - ConsumerBarrier
 * C0 - Consumer 0
 * C1 - Consumer 1
 * C2 - Consumer 2
 * </pre>
 */
@SuppressWarnings("unchecked")
public final class MultiCast1P3CPerfTest
{
    private static final int NUM_CONSUMERS = 3;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);
    private static final int RING_SIZE = 8192;
    private static final long ITERATIONS = 1000 * 1000 * 50;

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEntry> ringBuffer =
        new RingBuffer<ValueEntry>(ValueEntry.ENTRY_FACTORY, RING_SIZE,
                                   ClaimStrategy.Option.SINGLE_THREADED,
                                   WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<ValueEntry> consumerBarrier = ringBuffer.createConsumerBarrier();

    private final ValueMutationHandler[] handlers = new ValueMutationHandler[NUM_CONSUMERS];
    {
        handlers[0] = new ValueMutationHandler(Operation.ADDITION);
        handlers[1] = new ValueMutationHandler(Operation.SUBTRACTION);
        handlers[2] = new ValueMutationHandler(Operation.AND);
    }

    private final BatchConsumer[] batchConsumers = new BatchConsumer[NUM_CONSUMERS];
    {
        batchConsumers[0] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[0]);
        batchConsumers[1] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[1]);
        batchConsumers[2] = new BatchConsumer<ValueEntry>(consumerBarrier, handlers[2]);
    }

    private final ProducerBarrier<ValueEntry> producerBarrier = ringBuffer.createProducerBarrier(0, batchConsumers);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final ArrayBlockingQueue<Long>[] blockingQueues = new ArrayBlockingQueue[NUM_CONSUMERS];
    {
        blockingQueues[0] = new ArrayBlockingQueue<Long>(RING_SIZE);
        blockingQueues[1] = new ArrayBlockingQueue<Long>(RING_SIZE);
        blockingQueues[2] = new ArrayBlockingQueue<Long>(RING_SIZE);
    }

    private final ValueMutationQueueConsumer[] queueConsumers = new ValueMutationQueueConsumer[NUM_CONSUMERS];
    {
        queueConsumers[0] = new ValueMutationQueueConsumer(blockingQueues[0], Operation.ADDITION);
        queueConsumers[1] = new ValueMutationQueueConsumer(blockingQueues[1], Operation.SUBTRACTION);
        queueConsumers[2] = new ValueMutationQueueConsumer(blockingQueues[2], Operation.AND);
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
            disruptorOps = runDisruptorPass();

            System.out.format("%s OpsPerSecond run %d: BlockingQueues=%d, Disruptor=%d\n",
                              getClass().getSimpleName(), Integer.valueOf(i), Long.valueOf(queueOps), Long.valueOf(disruptorOps));
        }

        Assert.assertTrue("Performance degraded", disruptorOps > queueOps);
    }

    private long runQueuePass() throws InterruptedException
    {
        Future[] futures = new Future[NUM_CONSUMERS];
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            queueConsumers[i].reset();
            futures[i] = EXECUTOR.submit(queueConsumers[i]);
        }

        long[] values = new long[NUM_CONSUMERS];
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            final Long value = Long.valueOf(i);
            blockingQueues[0].put(value);
            blockingQueues[1].put(value);
            blockingQueues[2].put(value);

            values[0] = Operation.ADDITION.op(values[0], i);
            values[1] = Operation.SUBTRACTION.op(values[1], i);
            values[2] = Operation.AND.op(values[2], i);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (getMinimumSequence(queueConsumers) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            queueConsumers[i].halt();
            futures[i].cancel(true);
            Assert.assertEquals(values[i], queueConsumers[i].getValue());
        }

        return opsPerSecond;
    }

    private long getMinimumSequence(final ValueMutationQueueConsumer[] queueConsumers)
    {
        long minimum = Long.MAX_VALUE;

        for (ValueMutationQueueConsumer consumer : queueConsumers)
        {
            long sequence = consumer.getSequence();
            minimum = minimum < sequence ? minimum : sequence;
        }

        return minimum;
    }

    private long runDisruptorPass()
    {
        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            handlers[i].reset();
            EXECUTOR.submit(batchConsumers[i]);
        }

        long[] values = new long[NUM_CONSUMERS];
        long start = System.currentTimeMillis();

        for (long i = 0; i < ITERATIONS; i++)
        {
            ValueEntry entry = producerBarrier.claimNext();
            entry.setValue(i);
            entry.commit();

            values[0] = Operation.ADDITION.op(values[0], i);
            values[1] = Operation.SUBTRACTION.op(values[1], i);
            values[2] = Operation.AND.op(values[2], i);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (Util.getMinimumSequence(batchConsumers) < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000) / (System.currentTimeMillis() - start);

        for (int i = 0; i < NUM_CONSUMERS; i++)
        {
            batchConsumers[i].halt();
            Assert.assertEquals(values[i], handlers[i].getValue());
        }

        return opsPerSecond;
    }

    public static final class ValueMutationQueueConsumer implements Runnable
    {
        private volatile boolean running;
        private volatile long sequence;
        private long value;

        private final BlockingQueue<Long> blockingQueue;
        private final Operation operation;

        public ValueMutationQueueConsumer(final BlockingQueue<Long> blockingQueue, final Operation operation)
        {
            this.blockingQueue = blockingQueue;
            this.operation = operation;
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
                    this.value = operation.op(this.value, value);
                    sequence = value;
                }
                catch (InterruptedException ex)
                {
                    break;
                }
            }
        }
    }

    public static final class ValueMutationHandler implements BatchHandler<ValueEntry>
    {
        private final Operation operation;
        private long value;

        public ValueMutationHandler(final Operation operation)
        {
            this.operation = operation;
        }

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
            value = operation.op(value, entry.getValue());
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
