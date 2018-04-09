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
package com.lmax.disruptor.translator;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.support.PerfTestUtil;
import com.lmax.disruptor.support.ValueAdditionEventHandler;
import com.lmax.disruptor.support.ValueEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.lmax.disruptor.util.MutableLong;

import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.support.PerfTestUtil.failIfNot;

/**
 * <pre>
 * UniCast a series of items between 1 publisher and 1 event processor using the EventTranslator API
 *
 * +----+    +-----+
 * | P1 |--->| EP1 |
 * +----+    +-----+
 *
 * Disruptor:
 * ==========
 *              track to prevent wrap
 *              +------------------+
 *              |                  |
 *              |                  v
 * +----+    +====+    +====+   +-----+
 * | P1 |--->| RB |<---| SB |   | EP1 |
 * +----+    +====+    +====+   +-----+
 *      claim      get    ^        |
 *                        |        |
 *                        +--------+
 *                          waitFor
 *
 * P1  - Publisher 1
 * RB  - RingBuffer
 * SB  - SequenceBarrier
 * EP1 - EventProcessor 1
 *
 * </pre>
 */
public final class OneToOneTranslatorThroughputTest extends AbstractPerfTestDisruptor
{
    private static final int BUFFER_SIZE = 1024 * 64;
    private static final long ITERATIONS = 1000L * 1000L * 100L;
    private final long expectedResult = PerfTestUtil.accumulatedAddition(ITERATIONS);
    private final ValueAdditionEventHandler handler = new ValueAdditionEventHandler();
    private final RingBuffer<ValueEvent> ringBuffer;
    private final MutableLong value = new MutableLong(0);

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("unchecked")
    public OneToOneTranslatorThroughputTest()
    {
        Disruptor<ValueEvent> disruptor =
            new Disruptor<ValueEvent>(
                ValueEvent.EVENT_FACTORY,
                BUFFER_SIZE, DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy());
        disruptor.handleEventsWith(handler);
        this.ringBuffer = disruptor.start();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected int getRequiredProcessorCount()
    {
        return 2;
    }

    @Override
    protected PerfTestContext runDisruptorPass() throws InterruptedException
    {
        PerfTestContext perfTestContext = new PerfTestContext();
        MutableLong value = this.value;

        final CountDownLatch latch = new CountDownLatch(1);
        long expectedCount = ringBuffer.getMinimumGatingSequence() + ITERATIONS;

        handler.reset(latch, expectedCount);
        long start = System.currentTimeMillis();

        final RingBuffer<ValueEvent> rb = ringBuffer;

        for (long l = 0; l < ITERATIONS; l++)
        {
            value.set(l);
            rb.publishEvent(Translator.INSTANCE, value);
        }

        latch.await();
        perfTestContext.setDisruptorOps((ITERATIONS * 1000L) / (System.currentTimeMillis() - start));
        perfTestContext.setBatchData(handler.getBatchesProcessed(), ITERATIONS);
        waitForEventProcessorSequence(expectedCount);

        failIfNot(expectedResult, handler.getValue());

        return perfTestContext;
    }

    private static class Translator implements EventTranslatorOneArg<ValueEvent, MutableLong>
    {
        private static final Translator INSTANCE = new Translator();

        @Override
        public void translateTo(ValueEvent event, long sequence, MutableLong arg0)
        {
            event.setValue(arg0.get());
        }
    }

    private void waitForEventProcessorSequence(long expectedCount) throws InterruptedException
    {
        while (ringBuffer.getMinimumGatingSequence() != expectedCount)
        {
            Thread.sleep(1);
        }
    }

    public static void main(String[] args) throws Exception
    {
        OneToOneTranslatorThroughputTest test = new OneToOneTranslatorThroughputTest();
        test.testImplementations();
    }
}
