package com.lmax.disruptor;

import static com.lmax.disruptor.RingBuffer.createMultiProducer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramData.Percentiles;
import org.HdrHistogram.HistogramIterationValue;

import com.lmax.disruptor.util.DaemonThreadFactory;

public class MultiProducerLatencyPerfTest
{
    private static boolean DEBUG = true;
    
    private static class MyEvent
    {
        public static EventFactory<MyEvent> FACTORY = new EventFactory<MultiProducerLatencyPerfTest.MyEvent>()
        {
            @Override
            public MyEvent newInstance()
            {
                return new MyEvent();
            }
        };
        
        long timestamp;

        public void set(long t0)
        {
            timestamp = t0;
        }
    }

    private static final int SIZE = 1 << 20;
    
    private static class MyEventHandler implements EventHandler<MyEvent>
    {
        private final Histogram histogram = new Histogram(10000000, 3);
        private final long ignoreBelow;

        public MyEventHandler(long ignoreBelow)
        {
            this.ignoreBelow = ignoreBelow;
        }
        
        @Override
        public void onEvent(MyEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            if (sequence > ignoreBelow)
            {
                histogram.recordValue(System.nanoTime() - event.timestamp);
            }
            
            if (DEBUG && (sequence + 1) % 10000 == 0)
            {
                System.out.print("Received ");
                System.out.print(sequence + 1);
                System.out.print(" messages");
                System.out.println();
            }
        }

        public Histogram getHistogram()
        {
            return histogram;
        }
    }
    
    private static class Producer implements Runnable
    {
        private final int numIterations;
        private final CyclicBarrier cyclicBarrier;
        private final RingBuffer<MyEvent> ringBuffer;
        private final int ratePerSecond;
        private CountDownLatch stopLatch;

        public Producer(int numIterations, int ratePerSecond, RingBuffer<MyEvent> ringBuffer,
                        CyclicBarrier startBarrier, CountDownLatch stopLatch)
        {
            this.numIterations = numIterations;
            this.cyclicBarrier = startBarrier;
            this.ringBuffer = ringBuffer;
            this.ratePerSecond = ratePerSecond;
            this.stopLatch = stopLatch;
        }
        
        public void run()
        {
            awaitStart();
            
            int sent = 0;
            long startTime = System.nanoTime();
            long nanosPerEvent = TimeUnit.SECONDS.toNanos(1) / ratePerSecond;
            long sleepTime = nanosPerEvent >> 2;
            
            while (sent < numIterations)
            {
                long currentTime = System.nanoTime();
                long deltaTime = currentTime - startTime;
                long shouldHaveSent = deltaTime / nanosPerEvent;
                
                while (sent < shouldHaveSent)
                {
                    send(sent);
                    sent++;
                }
                LockSupport.parkNanos(sleepTime);
            }
            
            stopLatch.countDown();
        }

        private void send(int i)
        {
            long t0 = System.nanoTime();
            long next = ringBuffer.next();
            ringBuffer.getPreallocated(next).set(t0);
            ringBuffer.publish(next);
        }

        private void awaitStart()
        {
            try
            {
                cyclicBarrier.await();
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }
    }
    
    public static void main(String[] args) throws InterruptedException
    {
        int numProducers = 1;
        int numIterations = 100000;
        
        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        CyclicBarrier startBarrier = new CyclicBarrier(numProducers);
        CountDownLatch stopLatch = new CountDownLatch(numProducers);
        
//        final RingBuffer<MyEvent> ringBuffer = createMultiProducer(MyEvent.FACTORY, SIZE, new BusySpinWaitStrategy());
        final RingBuffer<MyEvent> ringBuffer = RingBuffer.createSingleProducer(MyEvent.FACTORY, SIZE, new BusySpinWaitStrategy());
        SequenceBarrier barrier = ringBuffer.newBarrier();
        MyEventHandler eventHandler = new MyEventHandler(50000);
        BatchEventProcessor<MyEvent> processor = 
                new BatchEventProcessor<MyEvent>(ringBuffer, barrier, eventHandler);
        ringBuffer.addGatingSequences(processor.getSequence());
        
        System.gc();
        Thread.sleep(1000);
        
        executor.submit(processor);
                
        Producer[] producers = new Producer[numProducers];
        for (int i = 0; i < producers.length; i++)
        {
            producers[i] = new Producer(numIterations, 2000, ringBuffer, startBarrier, stopLatch);
            executor.submit(producers[i]);
        }
        
        stopLatch.await();
        
        System.out.println("Latch done.");
        
        System.out.printf("Backoff Count: %d%n", ringBuffer.getBackOffCount());
        
        Percentiles percentiles = eventHandler.getHistogram().getHistogramData().percentiles(2);
        for (HistogramIterationValue v : percentiles)
        {
            System.out.printf("%.3f%%: %d, %d%n", 
                              v.getPercentile(), v.getValueIteratedTo(), v.getTotalCountToThisValue());
        }
    }
}
