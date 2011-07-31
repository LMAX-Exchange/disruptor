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

import com.lmax.disruptor.support.StubEntry;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.support.Actions.countDown;
import static org.junit.Assert.assertEquals;

@RunWith(JMock.class)
public final class BatchConsumerTest
{
    private final Mockery context = new Mockery();
    private final Sequence lifecycleSequence = context.sequence("lifecycleSequence");
    private final CountDownLatch latch = new CountDownLatch(1);

    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 16);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    @SuppressWarnings("unchecked") private final BatchHandler<StubEntry> batchHandler = context.mock(BatchHandler.class);
    private final BatchConsumer batchConsumer = new BatchConsumer<StubEntry>(consumerBarrier, batchHandler);
    {
        ringBuffer.setTrackedConsumers(batchConsumer);
    }

    @Test(expected = NullPointerException.class)
    public void shouldThrowExceptionOnSettingNullExceptionHandler()
    {
        batchConsumer.setExceptionHandler(null);
    }

    @Test
    public void shouldReturnUnderlyingBarrier()
    {
        assertEquals(consumerBarrier, batchConsumer.getConsumerBarrier());
    }

    @Test
    public void shouldCallMethodsInLifecycleOrder()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(batchHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);

                oneOf(batchHandler).onEndOfBatch();
                inSequence(lifecycleSequence);
                will(countDown(latch));
            }
        });

        Thread thread = new Thread(batchConsumer);
        thread.start();

        assertEquals(-1L, batchConsumer.getSequence());

        ringBuffer.commit(ringBuffer.nextEntry());

        latch.await();

        batchConsumer.halt();
        thread.join();
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        context.checking(new Expectations()
        {
            {
                oneOf(batchHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);
                oneOf(batchHandler).onAvailable(ringBuffer.getEntry(1));
                inSequence(lifecycleSequence);
                oneOf(batchHandler).onAvailable(ringBuffer.getEntry(2));
                inSequence(lifecycleSequence);

                oneOf(batchHandler).onEndOfBatch();
                inSequence(lifecycleSequence);
                will(countDown(latch));
            }
        });

        ringBuffer.commit(ringBuffer.nextEntry());
        ringBuffer.commit(ringBuffer.nextEntry());
        ringBuffer.commit(ringBuffer.nextEntry());

        Thread thread = new Thread(batchConsumer);
        thread.start();

        latch.await();

        batchConsumer.halt();
        thread.join();
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException()
        throws Exception
    {
        final Exception ex = new Exception();
        final ExceptionHandler exceptionHandler = context.mock(ExceptionHandler.class);
        batchConsumer.setExceptionHandler(exceptionHandler);

        context.checking(new Expectations()
        {
            {
                oneOf(batchHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);
                will(new Action()
                {
                    @Override
                    public Object invoke(final Invocation invocation) throws Throwable
                    {
                        throw ex;
                    }

                    @Override
                    public void describeTo(final Description description)
                    {
                        description.appendText("Throws exception");
                    }
                });

                oneOf(exceptionHandler).handle(ex, ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);
                will(countDown(latch));
            }
        });

        Thread thread = new Thread(batchConsumer);
        thread.start();

        ringBuffer.commit(ringBuffer.nextEntry());

        latch.await();

        batchConsumer.halt();
        thread.join();
    }
}
