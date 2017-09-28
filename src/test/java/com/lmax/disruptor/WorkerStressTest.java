package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.LockSupport;

import static java.lang.Math.max;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

public class WorkerStressTest
{
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @Test
    public void shouldHandleLotsOfThreads() throws Exception
    {
        Disruptor<TestEvent> disruptor = new Disruptor<TestEvent>(
            TestEvent.FACTORY, 1 << 16, DaemonThreadFactory.INSTANCE,
            ProducerType.MULTI, new SleepingWaitStrategy());
        RingBuffer<TestEvent> ringBuffer = disruptor.getRingBuffer();
        disruptor.setDefaultExceptionHandler(new FatalExceptionHandler());

        int threads = max(1, Runtime.getRuntime().availableProcessors() / 2);

        int iterations = 200000;
        int publisherCount = threads;
        int handlerCount = threads;

        CyclicBarrier barrier = new CyclicBarrier(publisherCount);
        CountDownLatch latch = new CountDownLatch(publisherCount);

        TestWorkHandler[] handlers = initialise(new TestWorkHandler[handlerCount]);
        Publisher[] publishers = initialise(new Publisher[publisherCount], ringBuffer, iterations, barrier, latch);

        disruptor.handleEventsWithWorkerPool(handlers);

        disruptor.start();

        for (Publisher publisher : publishers)
        {
            executor.execute(publisher);
        }

        latch.await();
        while (ringBuffer.getCursor() < (iterations - 1))
        {
            LockSupport.parkNanos(1);
        }

        disruptor.shutdown();

        for (Publisher publisher : publishers)
        {
            assertThat(publisher.failed, is(false));
        }

        for (TestWorkHandler handler : handlers)
        {
            assertThat(handler.seen, is(not(0)));
        }
    }

    private Publisher[] initialise(
        Publisher[] publishers, RingBuffer<TestEvent> buffer,
        int messageCount, CyclicBarrier barrier, CountDownLatch latch)
    {
        for (int i = 0; i < publishers.length; i++)
        {
            publishers[i] = new Publisher(buffer, messageCount, barrier, latch);
        }

        return publishers;
    }

    @SuppressWarnings("unchecked")
    private TestWorkHandler[] initialise(TestWorkHandler[] testEventHandlers)
    {
        for (int i = 0; i < testEventHandlers.length; i++)
        {
            TestWorkHandler handler = new TestWorkHandler();
            testEventHandlers[i] = handler;
        }

        return testEventHandlers;
    }

    private static class TestWorkHandler implements WorkHandler<TestEvent>
    {
        private int seen;

        @Override
        public void onEvent(TestEvent event) throws Exception
        {
            seen++;
        }
    }

    private static class Publisher implements Runnable
    {
        private final RingBuffer<TestEvent> ringBuffer;
        private final CyclicBarrier barrier;
        private final int iterations;
        private final CountDownLatch shutdownLatch;

        public boolean failed = false;

        Publisher(
            RingBuffer<TestEvent> ringBuffer,
            int iterations,
            CyclicBarrier barrier,
            CountDownLatch shutdownLatch)
        {
            this.ringBuffer = ringBuffer;
            this.barrier = barrier;
            this.iterations = iterations;
            this.shutdownLatch = shutdownLatch;
        }

        @Override
        public void run()
        {
            try
            {
                barrier.await();

                int i = iterations;
                while (--i != -1)
                {
                    long next = ringBuffer.next();
                    TestEvent testEvent = ringBuffer.get(next);
                    testEvent.sequence = next;
                    testEvent.a = next + 13;
                    testEvent.b = next - 7;
                    testEvent.s = "wibble-" + next;
                    ringBuffer.publish(next);
                }
            }
            catch (Exception e)
            {
                failed = true;
            }
            finally
            {
                shutdownLatch.countDown();
            }
        }
    }

    private static class TestEvent
    {
        public long sequence;
        public long a;
        public long b;
        public String s;

        public static final EventFactory<TestEvent> FACTORY = new EventFactory<WorkerStressTest.TestEvent>()
        {
            @Override
            public TestEvent newInstance()
            {
                return new TestEvent();
            }
        };
    }
}
