package com.lmax.disruptor;

import com.lmax.disruptor.EventPoller.PollState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class EventPollerTest
{
    @Test
    @SuppressWarnings("unchecked")
    public void shouldPollForEvents() throws Exception
    {
        final Sequence gatingSequence = new Sequence();
        final SingleProducerSequencer sequencer = new SingleProducerSequencer(16, new BusySpinWaitStrategy());
        final EventPoller.Handler<Object> handler = (event, sequence, endOfBatch) -> false;

        final Object[] data = new Object[16];
        final DataProvider<Object> provider = sequence -> data[(int) sequence];

        final EventPoller<Object> poller = sequencer.newPoller(provider, gatingSequence);
        final Object event = new Object();
        data[0] = event;

        assertThat(poller.poll(handler), is(PollState.IDLE));

        // Publish Event.
        sequencer.publish(sequencer.next());
        assertThat(poller.poll(handler), is(PollState.GATING));

        gatingSequence.incrementAndGet();
        assertThat(poller.poll(handler), is(PollState.PROCESSING));
    }

    @Test
    public void shouldSuccessfullyPollWhenBufferIsFull() throws Exception
    {
        final ArrayList<byte[]> events = new ArrayList<>();

        final EventPoller.Handler<byte[]> handler = (event, sequence, endOfBatch) ->
        {
            events.add(event);
            return !endOfBatch;
        };

        EventFactory<byte[]> factory = () -> new byte[1];

        final RingBuffer<byte[]> ringBuffer = RingBuffer.createMultiProducer(factory, 4, new SleepingWaitStrategy());

        final EventPoller<byte[]> poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());

        int count = 4;

        for (byte i = 1; i <= count; ++i)
        {
            long next = ringBuffer.next();
            ringBuffer.get(next)[0] = i;
            ringBuffer.publish(next);
        }

        // think of another thread
        poller.poll(handler);

        assertThat(events.size(), is(4));
    }

    @Test
    public void shouldPollRelaxedOnlyPublishedEvents() throws Exception
    {
        final ArrayList<Long> sequences = new ArrayList<>();
        final ArrayList<Boolean> endOfBatches = new ArrayList<>();
        final EventPoller.Handler<byte[]> handler = (event, sequence, endOfBatch) ->
        {
            sequences.add(sequence);
            endOfBatches.add(endOfBatch);
            return true;
        };

        final RingBuffer<byte[]> ringBuffer =
            RingBuffer.createMultiProducer(() -> new byte[1], 4, new BusySpinWaitStrategy());
        final EventPoller<byte[]> poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());

        final long first = ringBuffer.next();
        final long second = ringBuffer.next();

        // Claimed, not published: poll reports that it is gated, pollRelaxed cannot tell.
        assertThat(poller.pollRelaxed(handler), is(PollState.IDLE));
        assertThat(poller.poll(handler), is(PollState.GATING));

        // Published behind an unpublished sequence: still not readable.
        ringBuffer.publish(second);
        assertThat(poller.pollRelaxed(handler), is(PollState.IDLE));

        ringBuffer.publish(first);
        assertThat(poller.pollRelaxed(handler), is(PollState.PROCESSING));
        assertThat(sequences, is(List.of(first, second)));
        assertThat(endOfBatches, is(List.of(false, true)));
        assertThat(poller.getSequence().get(), is(second));
    }

    @Test
    public void shouldPollRelaxedAFullBufferOnEveryLap() throws Exception
    {
        final int bufferSize = 4;
        final ArrayList<Long> sequences = new ArrayList<>();
        final EventPoller.Handler<byte[]> handler = (event, sequence, endOfBatch) -> sequences.add(sequence);

        final RingBuffer<byte[]> ringBuffer =
            RingBuffer.createMultiProducer(() -> new byte[1], bufferSize, new BusySpinWaitStrategy());
        final EventPoller<byte[]> poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());

        for (int lap = 1; lap <= 3; lap++)
        {
            // With the buffer full, the look ahead reaches the slot that the batch started from.
            for (int i = 0; i < bufferSize; i++)
            {
                ringBuffer.publish(ringBuffer.tryNext());
            }

            assertThat(poller.pollRelaxed(handler), is(PollState.PROCESSING));
            assertThat(sequences.size(), is(lap * bufferSize));
            assertThat(poller.pollRelaxed(handler), is(PollState.IDLE));
        }
    }

    @Test
    public void shouldStopPollRelaxedWhenHandlerReturnsFalse() throws Exception
    {
        final EventPoller.Handler<byte[]> handler = (event, sequence, endOfBatch) -> false;

        final RingBuffer<byte[]> ringBuffer =
            RingBuffer.createMultiProducer(() -> new byte[1], 4, new BusySpinWaitStrategy());
        final EventPoller<byte[]> poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());

        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        assertThat(poller.pollRelaxed(handler), is(PollState.PROCESSING));
        assertThat(poller.getSequence().get(), is(0L));
    }

    @Test
    public void shouldPollRelaxedBehindGatingSequence() throws Exception
    {
        final boolean[] lastEndOfBatch = {false};
        final EventPoller.Handler<byte[]> handler = (event, sequence, endOfBatch) ->
        {
            lastEndOfBatch[0] = endOfBatch;
            return true;
        };

        final Sequence gatingSequence = new Sequence();
        final RingBuffer<byte[]> ringBuffer =
            RingBuffer.createMultiProducer(() -> new byte[1], 4, new BusySpinWaitStrategy());
        final EventPoller<byte[]> poller = ringBuffer.newPoller(gatingSequence);

        final long first = ringBuffer.next();
        assertThat(poller.pollRelaxed(handler), is(PollState.IDLE));

        ringBuffer.publish(first);
        ringBuffer.publish(ringBuffer.next());
        ringBuffer.publish(ringBuffer.next());

        // Published, but the upstream consumer has not passed it.
        assertThat(poller.pollRelaxed(handler), is(PollState.GATING));

        gatingSequence.set(1);
        assertThat(poller.pollRelaxed(handler), is(PollState.PROCESSING));
        assertThat(poller.getSequence().get(), is(1L));
        assertThat(lastEndOfBatch[0], is(true));
    }
}
