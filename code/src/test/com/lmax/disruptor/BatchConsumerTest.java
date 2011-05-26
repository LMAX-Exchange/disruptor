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
    private final ProducerBarrier<StubEntry> producerBarrier = ringBuffer.createProducerBarrier(0, batchConsumer);

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

                oneOf(batchHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Thread thread = new Thread(batchConsumer);
        thread.start();

        assertEquals(-1L, batchConsumer.getSequence());

        producerBarrier.commit(producerBarrier.claim());

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

                oneOf(batchHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        producerBarrier.commit(producerBarrier.claim());
        producerBarrier.commit(producerBarrier.claim());
        producerBarrier.commit(producerBarrier.claim());

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

                oneOf(batchHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Thread thread = new Thread(batchConsumer);
        thread.start();

        producerBarrier.commit(producerBarrier.claim());

        latch.await();

        batchConsumer.halt();
        thread.join();
    }
}
