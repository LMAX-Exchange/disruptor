package com.lmax.disruptor;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.States;
import org.jmock.integration.junit4.JMock;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.lmax.disruptor.EventPoller.PollState;

@RunWith(JMock.class)
public class EventPollerTest
{
    private final Mockery mockery = new Mockery();

    @Test
    @SuppressWarnings("unchecked")
    public void shouldPollForEvents() throws Exception
    {
        final Sequence pollSequence = new Sequence();
        final Sequence bufferSequence = new Sequence();
        final Sequence gatingSequence = new Sequence();
        final Sequencer sequencer = mockery.mock(Sequencer.class);
        final EventPoller.Handler<Object> handler = mockery.mock(EventPoller.Handler.class);
        final DataProvider<Object> provider = mockery.mock(DataProvider.class);
        final EventPoller<Object> poller = EventPoller.newInstance(provider, sequencer, pollSequence, bufferSequence,
                                                                   gatingSequence);
        final Object event = new Object();

        final States states = mockery.states("polling");

        mockery.checking(new Expectations()
        {
            {
                allowing(sequencer).getCursor();
                will(returnValue(-1L));
                when(states.is("idle"));

                allowing(sequencer).getCursor();
                will(returnValue(0L));
                when(states.is("gating"));

                allowing(sequencer).getCursor();
                will(returnValue(0L));
                when(states.is("processing"));

                allowing(sequencer).getHighestPublishedSequence(0L, -1L);
                will(returnValue(-1L));

                allowing(sequencer).getHighestPublishedSequence(0L, 0L);
                will(returnValue(0L));

                allowing(provider).get(0);
                will(returnValue(event));
                when(states.is("processing"));

                one(handler).onEvent(event, 0, true);
                when(states.is("processing"));
            }
        });

        // Initial State - nothing published.
        states.become("idle");
        assertThat(poller.poll(handler), is(PollState.IDLE));

        // Publish Event.
        states.become("gating");
        bufferSequence.incrementAndGet();
        assertThat(poller.poll(handler), is(PollState.GATING));

        states.become("processing");
        gatingSequence.incrementAndGet();
        assertThat(poller.poll(handler), is(PollState.PROCESSING));
    }

    @Test
    public void shouldSuccessfullyPollWhenBufferIsFull() throws Exception
    {
        @SuppressWarnings("unchecked")
        final EventPoller.Handler<byte[]> handler = mockery.mock(EventPoller.Handler.class);

        EventFactory<byte[]> factory = new EventFactory<byte[]>()
        {
            @Override
            public byte[] newInstance()
            {
                return new byte[1];
            }
        };

        final RingBuffer<byte[]> ringBuffer = RingBuffer.createMultiProducer(factory, 0x4, new SleepingWaitStrategy());

        final EventPoller<byte[]> poller = ringBuffer.newPoller();
        ringBuffer.addGatingSequences(poller.getSequence());

        int count = 4;

        for (byte i = 1; i <= count; ++i)
        {
            long next = ringBuffer.next();
            ringBuffer.get(next)[0] = i;
            ringBuffer.publish(next);
        }

        mockery.checking(new Expectations()
        {
            {
                exactly(4).of(handler).onEvent(with(any(byte[].class)), with(any(Long.TYPE)), with(any(Boolean.TYPE)));
                will(returnValue(true));
            }
        });

        // think of another thread
        poller.poll(handler);
    }
}
