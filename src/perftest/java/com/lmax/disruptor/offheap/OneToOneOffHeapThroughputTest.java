package com.lmax.disruptor.offheap;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import com.lmax.disruptor.AbstractPerfTestDisruptor;
import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.Sequencer;
import com.lmax.disruptor.SingleProducerSequencer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.util.DaemonThreadFactory;

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
    protected long runDisruptorPass() throws Exception
    {
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
        long opsPerSecond = (ITERATIONS * 1000L) / (System.currentTimeMillis() - start);
        waitForEventProcessorSequence(expectedCount);
        processor.halt();

        return opsPerSecond;
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

    public static class ByteBufferHandler implements EventHandler<ByteBuffer>
    {
        private long total = 0;
        private long expectedCount;
        private CountDownLatch latch;

        @Override
        public void onEvent(ByteBuffer event, long sequence, boolean endOfBatch) throws Exception
        {
            for (int i = 0; i < BLOCK_SIZE; i += 8)
            {
                total += event.getLong();
            }

            if (--expectedCount == 0)
            {
                latch.countDown();
            }
        }

        public long getTotal()
        {
            return total;
        }

        public void reset(CountDownLatch latch, long expectedCount)
        {
            this.latch = latch;
            this.expectedCount = expectedCount;
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
                return buffer.duplicate();
            }
        };

        public OffHeapRingBuffer(Sequencer sequencer, int entrySize)
        {
            this.sequencer = sequencer;
            this.entrySize = entrySize;
            this.mask = sequencer.getBufferSize() - 1;
            buffer = ByteBuffer.allocateDirect(sequencer.getBufferSize() * entrySize);
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
