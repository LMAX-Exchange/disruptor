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
package com.lmax.disruptor.sequenced;

import static com.lmax.disruptor.RingBuffer.createSingleProducer;

import java.io.PrintStream;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.lmax.disruptor.*;
import org.HdrHistogram.Histogram;

import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

/**
 * <pre>
 *
 * Ping pongs between 2 event handlers and measures the latency of
 * a round trip.
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
 * <p>
 * Note: <b>This test is only useful on a system using an invariant TSC in user space from the System.nanoTime() call.</b>
 */
public final class PingPongSequencedLatencyTest
{
    private static final int BUFFER_SIZE = 1024;
    private static final long ITERATIONS = 100L * 1000L * 30L;
    private static final long PAUSE_NANOS = 1000L;
    private final ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);

    private final Histogram histogram = new Histogram(10000000000L, 4);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    private final RingBuffer<ValueEvent> pingBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new BlockingWaitStrategy());
    private final RingBuffer<ValueEvent> pongBuffer =
        createSingleProducer(ValueEvent.EVENT_FACTORY, BUFFER_SIZE, new BlockingWaitStrategy());

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

    public void shouldCompareDisruptorVsQueues() throws Exception
    {
        final int runs = 3;

        for (int i = 0; i < runs; i++)
        {
            System.gc();
            histogram.reset();

            runDisruptorPass();

            System.out.format("%s run %d Disruptor %s\n", getClass().getSimpleName(), Long.valueOf(i), histogram);
            dumpHistogram(histogram, System.out);
        }
    }

    private static void dumpHistogram(final Histogram histogram, final PrintStream out)
    {
        histogram.outputPercentileDistribution(out, 1, 1000.0);
    }

    private void runDisruptorPass() throws InterruptedException, BrokenBarrierException
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final CyclicBarrier barrier = new CyclicBarrier(3);
        pinger.reset(barrier, latch, histogram);
        ponger.reset(barrier);

        executor.submit(pongProcessor);
        executor.submit(pingProcessor);

        barrier.await();
        latch.await();

        pingProcessor.halt();
        pongProcessor.halt();
    }

    public static void main(final String[] args) throws Exception
    {
        final PingPongSequencedLatencyTest test = new PingPongSequencedLatencyTest();
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

        Pinger(final RingBuffer<ValueEvent> buffer, final long maxEvents, final long pauseTimeNs)
        {
            this.buffer = buffer;
            this.maxEvents = maxEvents;
            this.pauseTimeNs = pauseTimeNs;
        }

        @Override
        public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            final long t1 = System.nanoTime();

            histogram.recordValueWithExpectedInterval(t1 - t0, pauseTimeNs);

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
            final long next = buffer.next();
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
            catch (final Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onShutdown()
        {
        }

        public void reset(final CyclicBarrier barrier, final CountDownLatch latch, final Histogram histogram)
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

        Ponger(final RingBuffer<ValueEvent> buffer)
        {
            this.buffer = buffer;
        }

        @Override
        public void onEvent(final ValueEvent event, final long sequence, final boolean endOfBatch) throws Exception
        {
            final long next = buffer.next();
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
            catch (final Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onShutdown()
        {
        }

        public void reset(final CyclicBarrier barrier)
        {
            this.barrier = barrier;
        }
    }
}
