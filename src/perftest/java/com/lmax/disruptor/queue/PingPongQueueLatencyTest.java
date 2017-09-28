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
package com.lmax.disruptor.queue;

import java.io.PrintStream;
import java.util.concurrent.*;

import org.HdrHistogram.Histogram;

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
 * </pre>
 * <p>
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class PingPongQueueLatencyTest
{
    private static final int BUFFER_SIZE = 1024;
    private static final long ITERATIONS = 100L * 1000L * 30L;
    private static final long PAUSE_NANOS = 1000L;
    private final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

    private final Histogram histogram = new Histogram(10000000000L, 4);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final BlockingQueue<Long> pingQueue = new ArrayBlockingQueue<Long>(BUFFER_SIZE);
    private final BlockingQueue<Long> pongQueue = new ArrayBlockingQueue<Long>(BUFFER_SIZE);
    private final QueuePinger qPinger = new QueuePinger(pingQueue, pongQueue, ITERATIONS, PAUSE_NANOS);
    private final QueuePonger qPonger = new QueuePonger(pingQueue, pongQueue);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    public void testImplementation() throws Exception
    {
        final int runs = 3;

        for (int i = 0; i < runs; i++)
        {
            System.gc();
            histogram.reset();

            runQueuePass();

            System.out.format("%s run %d BlockingQueue %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(histogram, System.out);
        }
    }

    private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runQueuePass() throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final CyclicBarrier barrier = new CyclicBarrier(3);
        qPinger.reset(barrier, latch, histogram);
        qPonger.reset(barrier);

        final Future<?> pingFuture = executor.submit(qPinger);
        final Future<?> pongFuture = executor.submit(qPonger);

        barrier.await();
        latch.await();

        pingFuture.cancel(true);
        pongFuture.cancel(true);
    }

    public static void main(final String[] args) throws Exception
    {
        final PingPongQueueLatencyTest test = new PingPongQueueLatencyTest();
        test.testImplementation();
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

        QueuePinger(
            final BlockingQueue<Long> pingQueue, final BlockingQueue<Long> pongQueue, final long maxEvents,
            final long pauseTimeNs)
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

                long counter = 0;

                while (counter < maxEvents)
                {
                    final long t0 = System.nanoTime();
                    pingQueue.put(1L);
                    counter += pongQueue.take();
                    final long t1 = System.nanoTime();

                    histogram.recordValueWithExpectedInterval(t1 - t0, pauseTimeNs);

                    while (pauseTimeNs > (System.nanoTime() - t1))
                    {
                        Thread.yield();
                    }
                }

                latch.countDown();
            }
            catch (final Exception e)
            {
                e.printStackTrace();
                return;
            }
        }

        public void reset(final CyclicBarrier barrier, final CountDownLatch latch, final Histogram histogram)
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

        QueuePonger(final BlockingQueue<Long> pingQueue, final BlockingQueue<Long> pongQueue)
        {
            this.pingQueue = pingQueue;
            this.pongQueue = pongQueue;
        }

        @Override
        public void run()
        {
            final Thread thread = Thread.currentThread();
            try
            {
                barrier.await();

                while (!thread.isInterrupted())
                {
                    final Long value = pingQueue.take();
                    pongQueue.put(value);
                }
            }
            catch (final InterruptedException e)
            {
                // do-nothing.
            }
            catch (final Exception e)
            {
                e.printStackTrace();
            }
        }

        public void reset(final CyclicBarrier barrier)
        {
            this.barrier = barrier;
        }
    }
}
