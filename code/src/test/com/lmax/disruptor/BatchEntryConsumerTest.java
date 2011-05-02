package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

import static com.lmax.disruptor.support.Actions.countDown;

@RunWith(JMock.class)
public final class BatchEntryConsumerTest
{
    private final Mockery context = new Mockery();
    private final Sequence lifecycleSequence = context.sequence("lifecycleSequence");

    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 10);
    private final ThresholdBarrier<StubEntry> barrier = ringBuffer.createBarrier();
    @SuppressWarnings("unchecked") private final BatchEntryHandler<StubEntry> batchEntryHandler = context.mock(BatchEntryHandler.class);
    private final BatchEntryConsumer batchEntryConsumer = new BatchEntryConsumer<StubEntry>(barrier, batchEntryHandler);

    @Test
    public void shouldReturnProvidedBarrier()
    {
        Assert.assertEquals(barrier, batchEntryConsumer.getBarrier());
    }

    @Test
    public void shouldCallMethodsInLifecycleOrder()
        throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);

        context.checking(new Expectations()
        {
            {
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);

                oneOf(batchEntryHandler).onEndOfBatch();
                inSequence(lifecycleSequence);
                will(countDown(latch));

                oneOf(batchEntryHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        Assert.assertEquals(-1L, batchEntryConsumer.getSequence());

        Claimer<StubEntry> claimer = ringBuffer.createClaimer(0, batchEntryConsumer);
        claimer.claimNext().commit();

        latch.await();

        batchEntryConsumer.halt();
        thread.join();
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);

        context.checking(new Expectations()
        {
            {
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(1));
                inSequence(lifecycleSequence);
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(2));
                inSequence(lifecycleSequence);

                oneOf(batchEntryHandler).onEndOfBatch();
                inSequence(lifecycleSequence);
                will(countDown(latch));

                oneOf(batchEntryHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Claimer<StubEntry> claimer = ringBuffer.createClaimer(0, batchEntryConsumer);
        claimer.claimNext().commit();
        claimer.claimNext().commit();
        claimer.claimNext().commit();

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        latch.await();

        batchEntryConsumer.halt();
        thread.join();
    }

    @Test
    public void shouldCallExceptionHandlerOnUncaughtException()
        throws Exception
    {
        final CountDownLatch latch = new CountDownLatch(1);
        final Exception ex = new Exception();
        final ExceptionHandler exceptionHandler = context.mock(ExceptionHandler.class);
        batchEntryConsumer.setExceptionHandler(exceptionHandler);

        context.checking(new Expectations()
        {
            {
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(0));
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

                oneOf(batchEntryHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        Claimer<StubEntry> claimer = ringBuffer.createClaimer(0, batchEntryConsumer);
        claimer.claimNext().commit();

        latch.await();

        batchEntryConsumer.halt();
        thread.join();
    }
}
