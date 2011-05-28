package com.lmax.disruptor;

import com.lmax.disruptor.support.FunctionStep;
import com.lmax.disruptor.support.FunctionEntry;
import com.lmax.disruptor.support.FunctionHandler;
import com.lmax.disruptor.support.FunctionQueueConsumer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.*;

/**
 * <pre>
 *
 * Pipeline a series of stages from a producer to ultimate consumer.
 * Each consumer depends on the output of the previous consumer.
 *
 * +----+    +----+    +----+    +----+
 * | P0 |--->| C0 |--->| C1 |--->| C2 |
 * +----+    +----+    +----+    +----+
 *
 *
 * Queue Based:
 * ============
 *
 *        put      take       put      take       put      take
 * +----+    +====+    +----+    +====+    +----+    +====+    +----+
 * | P0 |--->| Q0 |<---| C0 |--->| Q1 |<---| C1 |--->| Q2 |<---| C2 |
 * +----+    +====+    +----+    +====+    +----+    +====+    +----+
 *
 * P0 - Producer 0
 * Q0 - Queue 0
 * C0 - Consumer 0
 * Q1 - Queue 1
 * C1 - Consumer 1
 * Q2 - Queue 2
 * C2 - Consumer 1
 *
 *
 * Disruptor:
 * ==========
 *                   track to prevent wrap
 *             +------------------------------------------------------------------------+
 *             |                                                                        |
 *             |                                                                        v
 * +----+    +====+    +====+    +=====+    +----+    +=====+    +----+    +=====+    +----+
 * | P0 |--->| PB |--->| RB |    | CB0 |<---| C0 |<---| CB1 |<---| C1 |<---| CB2 |<---| C2 |
 * +----+    +====+    +====+    +=====+    +----+    +=====+    +----+    +=====+    +----+
 *                claim   ^  get    |  waitFor           |  waitFor           |  waitFor
 *                        |         |                    |                    |
 *                        +---------+--------------------+--------------------+
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
public final class Pipeline3StagePerfTest extends AbstractPerfTestQueueVsDisruptor
{
    private static final int NUM_CONSUMERS = 3;
    private static final int SIZE = 1024 * 32;
    private static final long ITERATIONS = 1000 * 1000 * 500;
    private final ExecutorService EXECUTOR = Executors.newFixedThreadPool(NUM_CONSUMERS);

    private static final long OPERAND_TWO_INITIAL_VALUE = 777L;
    private final long expectedResult;
    {
        long temp = 0L;
        long operandTwo = OPERAND_TWO_INITIAL_VALUE;

        for (long i = 0; i < ITERATIONS; i++)
        {
            long stageOneResult = i + operandTwo--;
            long stageTwoResult = stageOneResult + 3;

            if ((stageTwoResult & 4L) == 4L)
            {
                ++temp;
            }
        }

        expectedResult = temp;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<long[]> stepOneQueue = new ArrayBlockingQueue<long[]>(SIZE);
    private final BlockingQueue<Long> stepTwoQueue = new ArrayBlockingQueue<Long>(SIZE);
    private final BlockingQueue<Long> stepThreeQueue = new ArrayBlockingQueue<Long>(SIZE);

    private final FunctionQueueConsumer stepOneQueueConsumer =
        new FunctionQueueConsumer(FunctionStep.ONE, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueConsumer stepTwoQueueConsumer =
        new FunctionQueueConsumer(FunctionStep.TWO, stepOneQueue, stepTwoQueue, stepThreeQueue);
    private final FunctionQueueConsumer stepThreeQueueConsumer =
        new FunctionQueueConsumer(FunctionStep.THREE, stepOneQueue, stepTwoQueue, stepThreeQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<FunctionEntry> ringBuffer =
        new RingBuffer<FunctionEntry>(FunctionEntry.ENTRY_FACTORY, SIZE,
                                      ClaimStrategy.Option.SINGLE_THREADED,
                                      WaitStrategy.Option.YIELDING);

    private final ConsumerBarrier<FunctionEntry> stepOneConsumerBarrier = ringBuffer.createConsumerBarrier();
    private final FunctionHandler stepOneFunctionHandler = new FunctionHandler(FunctionStep.ONE);
    private final BatchConsumer<FunctionEntry> stepOneBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepOneConsumerBarrier, stepOneFunctionHandler);

    private final ConsumerBarrier<FunctionEntry> stepTwoConsumerBarrier = ringBuffer.createConsumerBarrier(stepOneBatchConsumer);
    private final FunctionHandler stepTwoFunctionHandler = new FunctionHandler(FunctionStep.TWO);
    private final BatchConsumer<FunctionEntry> stepTwoBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepTwoConsumerBarrier, stepTwoFunctionHandler);

    private final ConsumerBarrier<FunctionEntry> stepThreeConsumerBarrier = ringBuffer.createConsumerBarrier(stepTwoBatchConsumer);
    private final FunctionHandler stepThreeFunctionHandler = new FunctionHandler(FunctionStep.THREE);
    private final BatchConsumer<FunctionEntry> stepThreeBatchConsumer =
        new BatchConsumer<FunctionEntry>(stepThreeConsumerBarrier, stepThreeFunctionHandler);

    private final ProducerBarrier<FunctionEntry> producerBarrier = ringBuffer.createProducerBarrier(0, stepThreeBatchConsumer);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    @Override
    public void shouldCompareDisruptorVsQueues()
        throws Exception
    {
        testImplementations();
    }

    @Override
    protected long runDisruptorPass(final int passNumber)
    {
        stepThreeFunctionHandler.reset();

        EXECUTOR.submit(stepOneBatchConsumer);
        EXECUTOR.submit(stepTwoBatchConsumer);
        EXECUTOR.submit(stepThreeBatchConsumer);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            FunctionEntry entry = producerBarrier.nextEntry();
            entry.setOperandOne(i);
            entry.setOperandTwo(operandTwo--);
            producerBarrier.commit(entry);
        }

        final long expectedSequence = ringBuffer.getCursor();
        while (stepThreeBatchConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneBatchConsumer.halt();
        stepTwoBatchConsumer.halt();
        stepThreeBatchConsumer.halt();

        Assert.assertEquals(expectedResult, stepThreeFunctionHandler.getStepThreeCounter());

        return opsPerSecond;
    }

    @Override
    protected long runQueuePass(final int passNumber) throws Exception
    {
        stepThreeQueueConsumer.reset();

        Future[] futures = new Future[NUM_CONSUMERS];
        futures[0] = EXECUTOR.submit(stepOneQueueConsumer);
        futures[1] = EXECUTOR.submit(stepTwoQueueConsumer);
        futures[2] = EXECUTOR.submit(stepThreeQueueConsumer);

        long start = System.currentTimeMillis();

        long operandTwo = OPERAND_TWO_INITIAL_VALUE;
        for (long i = 0; i < ITERATIONS; i++)
        {
            long[] values = new long[2];
            values[0] = i;
            values[1] = operandTwo--;
            stepOneQueue.put(values);
        }

        final long expectedSequence = ITERATIONS - 1;
        while (stepThreeQueueConsumer.getSequence() < expectedSequence)
        {
            // busy spin
        }

        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);

        stepOneQueueConsumer.halt();
        stepTwoQueueConsumer.halt();
        stepThreeQueueConsumer.halt();

        for (Future future : futures)
        {
            future.cancel(true);
        }

        Assert.assertEquals(expectedResult, stepThreeQueueConsumer.getStepThreeCounter());

        return opsPerSecond;
    }
}
