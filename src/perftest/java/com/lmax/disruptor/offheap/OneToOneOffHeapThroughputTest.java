package com.lmax.disruptor.offheap;

import com.lmax.disruptor.*;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.PaddedLong;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

public class OneToOneOffHeapThroughputTest extends AbstractPerfTestDisruptor
{
    private static final int BLOCK_SIZE = 256;
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final long ITERATIONS = 1000 * 1000 * 10L;

    private final Executor executor = Executors.newFixedThreadPool(1, DaemonThreadFactory.INSTANCE);
    private final WaitStrategy waitStrategy = new YieldingWaitStrategy();
    private final OffHeapRingBuffer buffer =
        new OffHeapRingBuffer(new SingleProducerSequencer(BUFFER_SIZE, waitStrategy), BLOCK_SIZE);
    private final ByteBufferHandler handler = new ByteBufferHandler();
    private final BatchEventProcessor<ByteBuffer> processor =
        new BatchEventProcessor<ByteBuffer>(buffer, buffer.newBarrier(), handler);

    {
        buffer.addGatingSequences(processor.getSequence());
    }

    private final Random r = new Random(1);
    private final byte[] data = new byte[BLOCK_SIZE];

    public OneToOneOffHeapThroughputTest()
    {
        r.nextBytes(data);
    }

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws Exception
    {
        PerfTestContext perfTestContext = new PerfTestContext();
        byte[] data = this.data;

        final CountDownLatch latch = new CountDownLatch(1);
        long expectedCount = processor.getSequence().get() + ITERATIONS;
        handler.reset(latch, ITERATIONS);
        executor.execute(processor);
        long start = System.currentTimeMillis();

        final OffHeapRingBuffer rb = buffer;

        for (long i = 0; i < ITERATIONS; i++)
        {
            rb.put(data);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        perfTestContext.setBatchData(handler.getBatchesProcessed(), ITERATIONS);
        waitForEventProcessorSequence(expectedCount);
        processor.halt();

        return perfTestContext;
    }

    private void waitForEventProcessorSequence(long expectedCount)
    {
        while (processor.getSequence().get() < expectedCount)
        {
            LockSupport.parkNanos(1);
        }
    }

    public static void main(String[] args) throws Exception
    {
        new OneToOneOffHeapThroughputTest().testImplementations();
    }

    public static class ByteBufferHandler implements EventHandler<ByteBuffer>, BatchStartAware
    {
        private final PaddedLong total = new PaddedLong();
        private final PaddedLong batchesProcessed = new PaddedLong();
        private long expectedCount;
        private CountDownLatch latch;

        @Override
        public void onEvent(ByteBuffer event, long sequence, boolean endOfBatch) throws Exception
        {
            final int start = event.position();
            for (int i = start, size = start + BLOCK_SIZE; i < size; i += 8)
            {
                total.set(total.get() + event.getLong(i));
            }

            if (--expectedCount == 0)
            {
                latch.countDown();
            }
        }

        public long getTotal()
        {
            return total.get();
        }

        public long getBatchesProcessed()
        {
            return batchesProcessed.get();
        }

        public void reset(CountDownLatch latch, long expectedCount)
        {
            this.latch = latch;
            this.expectedCount = expectedCount;
            this.total.set(0);
            this.batchesProcessed.set(0);
        }

        @Override
        public void onBatchStart(long batchSize)
        {
            batchesProcessed.increment();
        }
    }

    public static class OffHeapRingBuffer implements DataProvider<ByteBuffer>
    {
        private final Sequencer sequencer;
        private final int entrySize;
        private final ByteBuffer buffer;
        private final int mask;

        private final ThreadLocal<ByteBuffer> perThreadBuffer = new ThreadLocal<ByteBuffer>()
        {
            @Override
            protected ByteBuffer initialValue()
            {
                return buffer.duplicate().order(ByteOrder.nativeOrder());
            }
        };

        public OffHeapRingBuffer(Sequencer sequencer, int entrySize)
        {
            this.sequencer = sequencer;
            this.entrySize = entrySize;
            this.mask = sequencer.getBufferSize() - 1;
            buffer = ByteBuffer.allocateDirect(sequencer.getBufferSize() * entrySize).order(ByteOrder.nativeOrder());
        }

        public void addGatingSequences(Sequence sequence)
        {
            sequencer.addGatingSequences(sequence);
        }

        public SequenceBarrier newBarrier()
        {
            return sequencer.newBarrier();
        }

        @Override
        public ByteBuffer get(long sequence)
        {
            int index = index(sequence);
            int position = index * entrySize;
            int limit = position + entrySize;

            ByteBuffer byteBuffer = perThreadBuffer.get();
            byteBuffer.position(position).limit(limit);

            return byteBuffer;
        }

        public void put(byte[] data)
        {
            long next = sequencer.next();
            try
            {
                get(next).put(data);
            }
            finally
            {
                sequencer.publish(next);
            }
        }

        private int index(long next)
        {
            return (int) (next & mask);
        }
    }
}
