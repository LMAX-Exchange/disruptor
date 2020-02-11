package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author : Rookiex
 * @Date : Created in 2020/2/11 10:45
 * @Describe :
 * @version:
 */
public class RemoveWorkHandlerTest
{

    @Test
    public void removeWorkHandlerLostEventExample() throws InterruptedException
    {
        int eventSize = 8;
        Set<Integer> data = initData(0, eventSize);
        CountDownLatch countDownLatch = new CountDownLatch(2 * eventSize);
        AtomicInteger count = new AtomicInteger();

        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<>(
                StubEvent.EVENT_FACTORY, 16, DaemonThreadFactory.INSTANCE);
        RingBuffer<StubEvent> ringBuffer = disruptor.start();

        // Construct 2 batch event processors.
        DynamicHandler handler1 = new DynamicHandler(count, countDownLatch);
        WorkProcessor<StubEvent> processor1 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler1, new FatalExceptionHandler(), workSequence);

        DynamicHandler handler2 = new DynamicHandler(count, countDownLatch);
        WorkProcessor<StubEvent> processor2 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler2, new FatalExceptionHandler(), workSequence);

        // Dynamically add both sequences to the ring buffer
        ringBuffer.addGatingSequences(processor1.getSequence());
        // Start the new batch processors.
        executor.execute(processor1);

        ringBuffer.addGatingSequences(processor2.getSequence());
        executor.execute(processor2);

        //wait handler start
        handler1.awaitStart();
        handler2.awaitStart();

        //add event
        new MessageProducer(disruptor, data).addEvent();

        // use halt remove a processor will lost a event
        processor1.halt();

        //Make sure new event are added after processor1.halt()
        new MessageProducer(disruptor, initData(eventSize, eventSize)).addEvent();

        //waiting remove complete
        handler1.awaitShutdown();

        ringBuffer.removeGatingSequence(processor1.getSequence());

        //waiting handler consume event(Because there is a event lost, it will be blocked here)
        boolean await = countDownLatch.await(3, TimeUnit.SECONDS);

        Assert.assertFalse(await);
        long lastCount = countDownLatch.getCount();
        int countValue = count.get();
        Assert.assertEquals(lastCount + countValue, eventSize * 2);
        Assert.assertTrue(lastCount > 0);
    }

    @Test
    public void removeWorkHandlerLaterTest() throws InterruptedException
    {
        int eventSize = 8;
        CountDownLatch countDownLatch = new CountDownLatch(2 * eventSize);
        AtomicInteger count = new AtomicInteger();

        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<>(
                StubEvent.EVENT_FACTORY, 16, DaemonThreadFactory.INSTANCE);
        RingBuffer<StubEvent> ringBuffer = disruptor.start();

        // Construct 2 batch event processors.
        DynamicHandler handler1 = new DynamicHandler(count, countDownLatch);
        WorkProcessor<StubEvent> processor1 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler1, new FatalExceptionHandler(), workSequence);

        DynamicHandler handler2 = new DynamicHandler(count, countDownLatch);
        WorkProcessor<StubEvent> processor2 =
                new WorkProcessor<>(ringBuffer, ringBuffer.newBarrier(), handler2, new FatalExceptionHandler(), workSequence);

        // Dynamically add both sequences to the ring buffer
        ringBuffer.addGatingSequences(processor1.getSequence());
        // Start the new batch processors.
        executor.execute(processor1);

        ringBuffer.addGatingSequences(processor2.getSequence());
        executor.execute(processor2);

        //wait handler start
        handler1.awaitStart();
        handler2.awaitStart();

        new MessageProducer(disruptor, initData(0, eventSize)).addEvent();

        // haltLater will wait the last event consume
        processor1.haltLater();

        //Make sure new event are added after processor1.haltLater()
        new MessageProducer(disruptor, initData(eventSize, eventSize)).addEvent();


        handler1.awaitShutdown();

        ringBuffer.removeGatingSequence(processor1.getSequence());

        //waiting handler consume event(Because there is a event lost, it will be blocked here)
        Assert.assertTrue(countDownLatch.await(3, TimeUnit.SECONDS));
    }

    private Set<Integer> initData(int start, int size)
    {
        Set<Integer> dataSet = new ConcurrentSkipListSet<>();
        for (int i = start; i < size + start; i++) {
            dataSet.add(i);
        }
        return dataSet;
    }
}

class MessageProducer
{
    Disruptor<StubEvent> disruptor;
    Set<Integer> dataSet;

    MessageProducer(Disruptor<StubEvent> disruptor, Set<Integer> dataSet)
    {
        this.disruptor = disruptor;
        this.dataSet = dataSet;
    }

    void addEvent()
    {
        for (int i : dataSet)
        {
            RingBuffer<StubEvent> ringBuffer = disruptor.getRingBuffer();
            long sequence = ringBuffer.next();
            try
            {
                StubEvent event = ringBuffer.get(sequence);
                event.setValue(i);
            } finally
            {
                ringBuffer.publish(sequence);
            }
        }
    }
}

class DynamicHandler implements WorkHandler<StubEvent>, LifecycleAware
{
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final CountDownLatch startLatch = new CountDownLatch(1);

    private AtomicInteger completeCount;

    private CountDownLatch countdownlatch;

    DynamicHandler(AtomicInteger completeCount, CountDownLatch countdownlatch)
    {
        this.countdownlatch = countdownlatch;
        this.completeCount = completeCount;
    }

    @Override
    public void onStart()
    {
        startLatch.countDown();
    }

    @Override
    public void onShutdown()
    {
        shutdownLatch.countDown();
    }

    void awaitShutdown() throws InterruptedException
    {
        shutdownLatch.await();
    }

    void awaitStart() throws InterruptedException
    {
        startLatch.await();
    }

    @Override
    public void onEvent(StubEvent event) throws Exception
    {
        countdownlatch.countDown();
        completeCount.incrementAndGet();
    }
}

