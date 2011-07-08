package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEntry;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

public final class BatchProducerTest
{
    private final RingBuffer<StubEntry> ringBuffer = new RingBuffer<StubEntry>(StubEntry.ENTRY_FACTORY, 20);
    private final ConsumerBarrier<StubEntry> consumerBarrier = ringBuffer.createConsumerBarrier();
    private final ProducerBarrier<StubEntry> producerBarrier = ringBuffer.createProducerBarrier(new NoOpConsumer(ringBuffer));

    @Test
    public void shouldClaimBatchAndCommitBack() throws Exception
    {
        final int batchSize = 5;
        final SequenceBatch sequenceBatch = new SequenceBatch(batchSize);

        producerBarrier.nextEntries(sequenceBatch);

        assertThat(Long.valueOf(sequenceBatch.getStart()), is(Long.valueOf(0L)));
        assertThat(Long.valueOf(sequenceBatch.getEnd()), is(Long.valueOf(04L)));
        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(RingBuffer.INITIAL_CURSOR_VALUE)));

        producerBarrier.commit(sequenceBatch);

        assertThat(Long.valueOf(ringBuffer.getCursor()), is(Long.valueOf(batchSize - 1L)));
        assertThat(Long.valueOf(consumerBarrier.waitFor(0L)), is(Long.valueOf(batchSize - 1L)));
    }
}
