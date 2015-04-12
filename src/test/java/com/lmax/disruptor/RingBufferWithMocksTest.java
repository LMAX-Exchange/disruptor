package com.lmax.disruptor;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.integration.junit4.JMock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.lmax.disruptor.support.StubEvent;

@RunWith(JMock.class)
public class RingBufferWithMocksTest
{
    private final Mockery mockery = new Mockery();

    private RingBuffer<StubEvent> ringBuffer;
    private Sequencer sequencer;

    @Before
    public void setUp()
    {
        sequencer = mockery.mock(Sequencer.class);

        mockery.checking(new Expectations()
        {
            {
                allowing(sequencer).getBufferSize();
                will(returnValue(16));
            }
        });

        ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, sequencer);
    }

    @Test
    public void shouldDelgateNextAndPublish()
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(new Expectations()
        {
            {
                one(sequencer).next();
                inSequence(sequence);
                will(returnValue(34L));

                one(sequencer).publish(34L);
                inSequence(sequence);
            }
        });

        ringBuffer.publish(ringBuffer.next());
    }

    @Test
    public void shouldDelgateTryNextAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(new Expectations()
        {
            {
                one(sequencer).tryNext();
                inSequence(sequence);
                will(returnValue(34L));

                one(sequencer).publish(34L);
                inSequence(sequence);
            }
        });

        ringBuffer.publish(ringBuffer.tryNext());
    }

    @Test
    public void shouldDelgateNextNAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(new Expectations()
        {
            {
                one(sequencer).next(10);
                inSequence(sequence);
                will(returnValue(34L));

                one(sequencer).publish(25L, 34L);
                inSequence(sequence);
            }
        });

        long hi = ringBuffer.next(10);
        ringBuffer.publish(hi - 9, hi);
    }

    @Test
    public void shouldDelgateTryNextNAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(new Expectations()
        {
            {
                one(sequencer).tryNext(10);
                inSequence(sequence);
                will(returnValue(34L));

                one(sequencer).publish(25L, 34L);
                inSequence(sequence);
            }
        });

        long hi = ringBuffer.tryNext(10);
        ringBuffer.publish(hi - 9, hi);
    }
}
