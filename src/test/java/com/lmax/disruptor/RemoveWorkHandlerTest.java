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

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.support.StubEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RemoveWorkHandlerTest
{
    @Test
    public void removeWorkHandlerLostEventExample() throws InterruptedException
    {
        int eventSize = 8;
        CountDownLatch countDownLatch = new CountDownLatch(2 * eventSize);
        AtomicInteger count = new AtomicInteger();

        ExecutorService executor = Executors.newCachedThreadPool(DaemonThreadFactory.INSTANCE);
        Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

        // Build a disruptor and start it.
        Disruptor<StubEvent> disruptor = new Disruptor<>(
                StubEvent.EVENT_FACTORY, 4, DaemonThreadFactory.INSTANCE);
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
        MessageProducer producer1 = new MessageProducer(disruptor, initData(0, eventSize));
        new Thread(producer1).start();
        producer1.awaitStart();

        // use halt remove a processor will lost a event
        processor1.halt();

        //Make sure new event are added after processor1.halt()
        MessageProducer producer2 = new MessageProducer(disruptor, initData(eventSize, eventSize));
        new Thread(producer2).start();
        producer2.awaitStart();

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
                StubEvent.EVENT_FACTORY, 4, DaemonThreadFactory.INSTANCE);
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

        MessageProducer producer1 = new MessageProducer(disruptor, initData(0, eventSize));
        new Thread(producer1).start();
        producer1.awaitStart();

        // haltLater will wait the last event consume
        processor1.haltLater();

        //Make sure new event are added after processor1.haltLater()
        MessageProducer producer2 = new MessageProducer(disruptor, initData(eventSize, eventSize));
        new Thread(producer2).start();
        producer2.awaitStart();

        handler1.awaitShutdown();

        ringBuffer.removeGatingSequence(processor1.getSequence());

        //waiting handler consume event
        Assert.assertTrue(countDownLatch.await(3, TimeUnit.SECONDS));
    }

    private Set<Integer> initData(int start, int size)
    {
        Set<Integer> dataSet = new ConcurrentSkipListSet<>();
        for (int i = start; i < size + start; i++)
        {
            dataSet.add(i);
        }
        return dataSet;
    }
}

class MessageProducer implements Runnable
{
    private Disruptor<StubEvent> disruptor;
    private Set<Integer> dataSet;
    private CountDownLatch startLatch = new CountDownLatch(1);

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
            }
            finally
            {
                ringBuffer.publish(sequence);
            }
        }
    }

    /**
     * When an object implementing interface <code>Runnable</code> is used
     * to create a thread, starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may
     * take any action whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run()
    {
        startLatch.countDown();
        addEvent();
    }

    public void awaitStart() throws InterruptedException
    {
        startLatch.await();
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
        Thread.yield();
    }
}

