package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.Sequence;
import org.jmock.integration.junit4.JMock;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JMock.class)
public final class BatchEntryConsumerTest
{
    private final Mockery context = new Mockery();

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
        final Sequence lifecycleSequence = context.sequence("lifecycleSequence");

        context.checking(new Expectations()
        {
            {
                oneOf(batchEntryHandler).onAvailable(ringBuffer.getEntry(0));
                inSequence(lifecycleSequence);

                oneOf(batchEntryHandler).onEndOfBatch();
                inSequence(lifecycleSequence);

                oneOf(batchEntryHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        Assert.assertEquals(-1L, batchEntryConsumer.getSequence());

        ringBuffer.claimNext().commit();

        while (batchEntryConsumer.getSequence() != 0)
        {
            Thread.yield();
        }

        batchEntryConsumer.halt();
        thread.join();
    }

    @Test
    public void shouldCallMethodsInLifecycleOrderForBatch()
        throws Exception
    {
        final Sequence lifecycleSequence = context.sequence("lifecycleSequence");

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

                oneOf(batchEntryHandler).onCompletion();
                inSequence(lifecycleSequence);
            }
        });

        ringBuffer.claimNext().commit();
        ringBuffer.claimNext().commit();
        ringBuffer.claimNext().commit();

        Thread thread = new Thread(batchEntryConsumer);
        thread.start();

        while (batchEntryConsumer.getSequence() != 2)
        {
            Thread.yield();
        }

        batchEntryConsumer.halt();
        thread.join();
    }
}
