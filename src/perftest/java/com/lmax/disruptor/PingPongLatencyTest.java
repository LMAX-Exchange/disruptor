/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;
import static org.junit.Assert.assertTrue;

import java.io.PrintStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.HdrHistogram.Histogram;
import org.junit.Test;

import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * <pre>
 *
 * Ping pongs between 2 event handlers and measures the latency of
 * a round trip.
 *
 * Queue Based:
 * ============
 *               +---take---+
 *               |          |
 *               |          V
 *            +====+      +====+
 *    +------>| Q1 |      | P2 |-------+
 *    |       +====+      +====+       |
 *   put                              put
 *    |       +====+      +====+       |
 *    +-------| P1 |      | Q2 |<------+
 *            +====+      +====+
 *               ^          |
 *               |          |
 *               +---take---+
 *
 * P1 - QueuePinger
 * P2 - QueuePonger
 * Q1 - PingQueue
 * Q2 - PongQueue
 *
 * Disruptor:
 * ==========
 *               +----------+
 *               |          |
 *               |   get    V
 *  waitFor   +=====+    +=====+  claim
 *    +------>| SB2 |    | RB2 |<------+
 *    |       +=====+    +=====+       |
 *    |                                |
 * +-----+    +=====+    +=====+    +-----+
 * | EP1 |--->| RB1 |    | SB1 |<---| EP2 |
 * +-----+    +=====+    +=====+    +-----+
 *       claim   ^   get    |  waitFor
 *               |          |
 *               +----------+
 *
 * EP1 - Pinger
 * EP2 - Ponger
 * RB1 - PingBuffer
 * SB1 - PingBarrier
 * RB2 - PongBuffer
 * SB2 - PongBarrier
 *
 * </pre>
 *
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class PingPongLatencyTest
{
    private static final int BUFFER_SIZE = 1024;
    private static final long ITERATIONS = 1000L * 1000L * 30L;
    private static final long PAUSE_NANOS = 1000L;
    private final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

    private final Histogram histogram = new Histogram(10000000000L, 4);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> pingQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> pongQueue = new LinkedBlockingQueue<Long>(BUFFER_SIZE);
    private final QueuePinger qPinger = new QueuePinger(pingQueue, pongQueue, ITERATIONS, PAUSE_NANOS);
    private final QueuePonger qPonger = new QueuePonger(pingQueue, pongQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> pingBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());
    private final RingBuffer<ValueEvent> pongBuffer =
            createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new YieldingWaitStrategy());

    private final SequenceBarrier pongBarrier = pongBuffer.newBarrier();
    private final Pinger pinger = new Pinger(pingBuffer, ITERATIONS, PAUSE_NANOS);
    private final BatchEventProcessor<ValueEvent> pingProcessor =
        new BatchEventProcessor<ValueEvent>(pongBuffer, pongBarrier, pinger);

    private final SequenceBarrier pingBarrier = pingBuffer.newBarrier();
    private final Ponger ponger = new Ponger(pongBuffer);
    private final BatchEventProcessor<ValueEvent> pongProcessor =
        new BatchEventProcessor<ValueEvent>(pingBuffer, pingBarrier, ponger);
    {
        pingBuffer.addGatingSequences(pongProcessor.getSequence());
        pongBuffer.addGatingSequences(pingProcessor.getSequence());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        final int runs = 3;

        double[] queueMeanLatency = new double[runs];
        double[] disruptorMeanLatency = new double[runs];

        if ("true".equalsIgnoreCase(System.getProperty("com.lmax.runQueueTests", "true")))
        {
            for (int i = 0; i < runs; i++)
            {
                System.gc();
                histogram.reset();

                runQueuePass();

                assertTrue(histogram.getHistogramData().getTotalCount() >= ITERATIONS);
                queueMeanLatency[i] = histogram.getHistogramData().getMean();

                System.out.format("%s run %d BlockingQueue %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
                dumpHistogram(histogram, System.out);
            }
        }
        else
        {
            for (int i = 0; i < runs; i++)
            {
                queueMeanLatency[i] = Double.MAX_VALUE;
            }
        }

        for (int i = 0; i < runs; i++)
        {
            System.gc();
            histogram.reset();

            runDisruptorPass();

            assertTrue(histogram.getHistogramData().getTotalCount() >= ITERATIONS);
            disruptorMeanLatency[i] = histogram.getHistogramData().getMean();

            System.out.format("%s run %d Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(histogram, System.out);
        }

        for (int i = 0; i < runs; i++)
        {
            assertTrue("run: " + i, queueMeanLatency[i] > disruptorMeanLatency[i]);
        }
    }

    private static void dumpHistogram(Histogram histogram, final PrintStream out)
    {
        histogram.getHistogramData().outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runQueuePass() throws Exception
    {
        CountDownLatch latch = new CountDownLatch(1);
        CyclicBarrier barrier = new CyclicBarrier(3);
        qPinger.reset(barrier, latch, histogram);
        qPonger.reset(barrier);

        Future<?> pingFuture = executor.submit(qPinger);
        Future<?> pongFuture = executor.submit(qPonger);

        barrier.await();
        latch.await();

        pingFuture.cancel(true);
        pongFuture.cancel(true);
    }

    private void runDisruptorPass() throws InterruptedException, BrokenBarrierException
    {
        CountDownLatch latch = new CountDownLatch(1);
        CyclicBarrier barrier = new CyclicBarrier(3);
        pinger.reset(barrier, latch, histogram);
        ponger.reset(barrier);

        executor.submit(pongProcessor);
        executor.submit(pingProcessor);

        barrier.await();
        latch.await();

        pingProcessor.halt();
        pongProcessor.halt();
    }

    public static void main(String[] args) throws Exception
    {
        PingPongLatencyTest test = new PingPongLatencyTest();
        test.shouldCompareDisruptorVsQueues();
    }

    private static class Pinger implements EventHandler<ValueEvent>, LifecycleAware
    {
        private final RingBuffer<ValueEvent> buffer;
        private final long maxEvents;
        private final long pauseTimeNs;

        private long counter = 0;
        private CyclicBarrier barrier;
        private CountDownLatch latch;
        private Histogram histogram;
        private long t0;

        public Pinger(RingBuffer<ValueEvent> buffer, long maxEvents, long pauseTimeNs)
        {
            this.buffer = buffer;
            this.maxEvents = maxEvents;
            this.pauseTimeNs = pauseTimeNs;
        }

        @Override
        public void onEvent(ValueEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            long t1 = System.nanoTime();

            histogram.recordValue(t1 - t0, pauseTimeNs);

            if (event.getValue() < maxEvents)
            {
                while (pauseTimeNs > (System.nanoTime() - t1))
                {
                    Thread.yield();
                }

                send();
            }
            else
            {
                latch.countDown();
            }
        }

        private void send()
        {
            t0 = System.nanoTime();
            long next = buffer.next();
            buffer.get(next).setValue(counter);
            buffer.publish(next);

            counter++;
        }

        @Override
        public void onStart()
        {
            try
            {
                barrier.await();

                Thread.sleep(1000);
                send();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onShutdown()
        {
        }

        public void reset(CyclicBarrier barrier, CountDownLatch latch, Histogram histogram)
        {
            this.histogram = histogram;
            this.barrier = barrier;
            this.latch = latch;

            counter = 0;
        }
    }

    private static class Ponger implements EventHandler<ValueEvent>, LifecycleAware
    {
        private final RingBuffer<ValueEvent> buffer;

        private CyclicBarrier barrier;

        public Ponger(RingBuffer<ValueEvent> buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public void onEvent(ValueEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            long next = buffer.next();
            buffer.get(next).setValue(event.getValue());
            buffer.publish(next);
        }

        @Override
        public void onStart()
        {
            try
            {
                barrier.await();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onShutdown()
        {
        }

        public void reset(CyclicBarrier barrier)
        {
            this.barrier = barrier;
        }
    }

    private static class QueuePinger implements Runnable
    {
        private final BlockingQueue<Long> pingQueue;
        private final BlockingQueue<Long> pongQueue;
        private final long pauseTimeNs;

        private Histogram histogram;
        private CyclicBarrier barrier;
        private CountDownLatch latch;
        private long counter;
        private final long maxEvents;

        public QueuePinger(BlockingQueue<Long> pingQueue, BlockingQueue<Long> pongQueue, long maxEvents, long pauseTimeNs)
        {
            this.pingQueue = pingQueue;
            this.pongQueue = pongQueue;
            this.maxEvents = maxEvents;
            this.pauseTimeNs = pauseTimeNs;
        }

        @Override
        public void run()
        {
            try
            {
                barrier.await();

                Thread.sleep(1000);

                long response = -1;

                while (response < maxEvents)
                {
                    long t0 = System.nanoTime();
                    pingQueue.put(counter++);
                    response = pongQueue.take();
                    long t1 = System.nanoTime();

                    histogram.recordValue(t1 - t0, pauseTimeNs);

                    while (pauseTimeNs > (System.nanoTime() - t1))
                    {
                        Thread.yield();
                    }
                }

                latch.countDown();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                return;
            }
        }

        public void reset(CyclicBarrier barrier, CountDownLatch latch, Histogram histogram)
        {
            this.histogram = histogram;
            this.barrier = barrier;
            this.latch = latch;

            counter = 0;
        }
    }

    private static class QueuePonger implements Runnable
    {
        private final BlockingQueue<Long> pingQueue;
        private final BlockingQueue<Long> pongQueue;
        private CyclicBarrier barrier;

        public QueuePonger(BlockingQueue<Long> pingQueue, BlockingQueue<Long> pongQueue)
        {
            this.pingQueue = pingQueue;
            this.pongQueue = pongQueue;
        }

        @Override
        public void run()
        {
            Thread thread = Thread.currentThread();
            try
            {
                barrier.await();

                while (!thread.isInterrupted())
                {
                    Long value = pingQueue.take();
                    pongQueue.put(value);
                }
            }
            catch (InterruptedException e)
            {
                // do-nothing.
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        public void reset(CyclicBarrier barrier)
        {
            this.barrier = barrier;
        }
    }
}
