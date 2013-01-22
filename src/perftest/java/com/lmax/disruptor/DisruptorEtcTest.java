package com.lmax.disruptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.BatchEventProcessor;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;

public class DisruptorEtcTest
{

    public static class MyEvent
    {
        int val;
    }

    private static final boolean DEBUG = false;

    private static final int MAX_NBR_OF_PUBLISHERS = 2 * Runtime.getRuntime().availableProcessors();

    private static final int NBR_OF_RUNS = 4;

    private static final long MESSAGES = 10000000;
    private static final int RING_SIZE = 65536;

    private static long consumed;
    private static CountDownLatch terminationLatch;

    private static void resetCountDown()
    {
        consumed = 0;
        terminationLatch = new CountDownLatch(1);
    }

    private static void shutdownAndAwaitTermination(ExecutorService executorService)
    {
        executorService.shutdown();
        try
        {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unused")
    public static void main(String[] args)
    {

        for (int p = 1; p <= MAX_NBR_OF_PUBLISHERS; p *= 2)
        {
            System.out.println();
            System.out.println("--- Number of publishers = " + p);
            System.out.println();

            if (false)
            {
                if (p == 1)
                {
                    System.out.println("Process " + MESSAGES
                                       + " messages with Disruptor using ProducerType.SINGLE / SleepingWaitStrategy.");
                    for (int k = 0; k < NBR_OF_RUNS; k++)
                    {
                        testDisruptor(p, ProducerType.SINGLE, new SleepingWaitStrategy(), 0);
                    }
                    System.out.println();
                }
            }

            if (true)
            {
                System.out.println("Process " + MESSAGES
                                   + " messages with Disruptor using ProducerType.MULTI / BusySpinWaitStrategy.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testDisruptor(p, ProducerType.MULTI, new BusySpinWaitStrategy(), 0);
                }
                System.out.println();
            }

            if (false)
            {
                System.out.println("Process " + MESSAGES
                                   + " messages with Disruptor using ProducerType.MULTI / SleepingWaitStrategy.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testDisruptor(p, ProducerType.MULTI, new SleepingWaitStrategy(), 0);
                }
                System.out.println();
            }

            if (false)
            {
                System.out.println("Process " + MESSAGES
                                   + " messages with Disruptor using ProducerType.MULTI / BlockingWaitStrategy.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testDisruptor(p, ProducerType.MULTI, new BlockingWaitStrategy(), 0);
                }
                System.out.println();
            }
            
            if (true)
            {
                System.out.println("Process " + MESSAGES
                                   + " bulk put messages with Disruptor using ProducerType.MULTI / BlockingWaitStrategy.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testDisruptor(p, ProducerType.MULTI, new BlockingWaitStrategy(), 100);
                }
                System.out.println();
            }

            if (true)
            {
                System.out.println("Process " + MESSAGES
                                   + " messages with ArrayBlockingQueueEx using bulk takeAll and put.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testArrayBlockingQueue(p, false, 0);
                }
                System.out.println();
            }

            if (true)
            {
                System.out.println("Process " + MESSAGES
                                   + " messages with ArrayBlockingQueueEx using bulk takeAll and bulk putAll.");
                for (int k = 0; k < NBR_OF_RUNS; k++)
                {
                    testArrayBlockingQueue(p, true, 100);
                }
                System.out.println();
            }
        }
    }

    private static void testArrayBlockingQueue(final int nbrOfPub, final boolean bulkPut, final int batchPutSize)
    {
        resetCountDown();

        final EventHandler<MyEvent> handler = new EventHandler<MyEvent>()
        {
            public void onEvent(final MyEvent event, final long sequence, final boolean endOfBatch) throws Exception
            {
                consumed++;

                if (consumed == MESSAGES * nbrOfPub)
                {
                    if (DEBUG)
                    {
                        System.out.println("countDown");
                    }
                    terminationLatch.countDown();
                }
            }
        };

        final ArrayBlockingQueueEx<MyEvent> queue = new ArrayBlockingQueueEx<MyEvent>(RING_SIZE);

        // Each EventProcessor can run on a separate thread
        final ExecutorService subExecutor = Executors.newCachedThreadPool();
        subExecutor.execute(new Runnable()
        {

            List<MyEvent> list = new ArrayList<MyEvent>();

            public void run()
            {
                try
                {
                    while (true)
                    {
                        list.clear();
                        queue.takeAll(list);
                        for (int i = 0; i < list.size(); i++)
                        {
                            handler.onEvent(list.get(i), 0, false);
                        }
                    }
                }
                catch (InterruptedException e)
                {
                    // quiet
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });

        // Time for worker to start waiting.
        try
        {
            Thread.sleep(100);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        long start = System.nanoTime();

        final ExecutorService pubExecutor = Executors.newCachedThreadPool();
        for (int pub = 0; pub < nbrOfPub; pub++)
        {
            final int fpub = pub;
            pubExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    if (DEBUG)
                    {
                        System.out.println("pub " + fpub);
                    }
                    if (!bulkPut)
                    {
                        for (long produced = 0; produced < MESSAGES; produced++)
                        {
                            MyEvent event = new MyEvent();
                            event.val = 0;

                            try
                            {
                                queue.put(event);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                    else
                    {
                        final int NUM_BATCHES = (int) (MESSAGES / batchPutSize);

                        List<MyEvent> putMessages = new ArrayList<MyEvent>();

                        for (long batch = 0; batch < NUM_BATCHES; batch++)
                        {
                            putMessages.clear();
                            for (long batchIterator = 0; batchIterator < batchPutSize; batchIterator++)
                            {
                                MyEvent event = new MyEvent();
                                event.val = 0;
                                putMessages.add(event);
                            }

                            try
                            {
                                queue.putAll(putMessages);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }

        shutdownAndAwaitTermination(pubExecutor);

        try
        {
            terminationLatch.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        System.out.println(String.format("%s: %.3f", "Test took", ((double) (System.nanoTime() - start) / 1000000000)));

        subExecutor.shutdownNow(); // interrupts
        shutdownAndAwaitTermination(subExecutor);
    }

    private static void testDisruptor(final int nbrOfPub, ProducerType producerType, WaitStrategy waitStrategy,
                                      final int batchSize)
    {
        resetCountDown();

        final EventHandler<MyEvent> handler = new EventHandler<MyEvent>()
        {
            public void onEvent(final MyEvent event, final long sequence, final boolean endOfBatch) throws Exception
            {
                consumed++;

                if (consumed == MESSAGES * nbrOfPub)
                {
                    if (DEBUG)
                    {
                        System.out.println("countDown");
                    }
                    terminationLatch.countDown();
                }
            }
        };

        final RingBuffer<MyEvent> ringBuffer;
        if (producerType == ProducerType.SINGLE)
        {
            ringBuffer = RingBuffer.createSingleProducer(new EventFactory<MyEvent>()
            {
                public MyEvent newInstance()
                {
                    return new MyEvent();
                }
            }, RING_SIZE, waitStrategy);
        }
        else if (producerType == ProducerType.MULTI)
        {
            ringBuffer = RingBuffer.createMultiProducer(new EventFactory<MyEvent>()
            {
                public MyEvent newInstance()
                {
                    return new MyEvent();
                }
            }, RING_SIZE, waitStrategy);
        }
        else
        {
            throw new UnsupportedOperationException(producerType + " unsupported");
        }

        SequenceBarrier barrier = ringBuffer.newBarrier();
        BatchEventProcessor<MyEvent> eventProcessor = new BatchEventProcessor<MyEvent>(ringBuffer, barrier, handler);
        ringBuffer.addGatingSequences(eventProcessor.getSequence());

        // Each EventProcessor can run on a separate thread
        final ExecutorService subExecutor = Executors.newCachedThreadPool();
        subExecutor.execute(eventProcessor);

        // Time for worker to start waiting.
        try
        {
            Thread.sleep(100);
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        long start = System.nanoTime();

        final ExecutorService pubExecutor = Executors.newCachedThreadPool();
        for (int pub = 0; pub < nbrOfPub; pub++)
        {
            final int fpub = pub;
            pubExecutor.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    if (DEBUG)
                    {
                        System.out.println("pub " + fpub);
                    }
                    if (batchSize == 0)
                    {
                        for (long produced = 0; produced < MESSAGES; produced++)
                        {
                            long sequence = ringBuffer.next();
                            MyEvent event = ringBuffer.getPreallocated(sequence);
                            
                            event.val = 0;
                            
                            ringBuffer.publish(sequence);
                        }
                    }
                    else
                    {
                        for (long produced = 0; produced < MESSAGES; produced += batchSize)
                        {
                            long sequence = ringBuffer.next(batchSize);
                            long lo = sequence - batchSize;
                            for (long l = lo; l <= sequence; l++)
                            {
                                MyEvent event = ringBuffer.getPreallocated(l);                                
                                event.val = 0;
                            }
                            
                            ringBuffer.publish(lo, sequence);
                        }
                    }
                }
            });
        }

        shutdownAndAwaitTermination(pubExecutor);

        try
        {
            terminationLatch.await();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }

        System.out.println(String.format("%s: %.3f", "Test took", ((double) (System.nanoTime() - start) / 1000000000)));

        eventProcessor.halt();
        shutdownAndAwaitTermination(subExecutor);
    }
}
