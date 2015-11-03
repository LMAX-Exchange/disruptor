package com.lmax.disruptor;

import com.lmax.disruptor.support.StubEvent;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class RingBufferWithMocksTest
{
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    private RingBuffer<StubEvent> ringBuffer;
    private Sequencer sequencer;

    @Before
    public void setUp()
    {
        sequencer = mockery.mock(Sequencer.class);

        mockery.checking(
            new Expectations()
            {
                {
                    allowing(sequencer).getBufferSize();
                    will(returnValue(16));
                }
            });

        ringBuffer = new RingBuffer<StubEvent>(StubEvent.EVENT_FACTORY, sequencer);
    }

    @Test
    public void shouldDelegateNextAndPublish()
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(
            new Expectations()
            {
                {
                    oneOf(sequencer).next();
                    inSequence(sequence);
                    will(returnValue(34L));

                    oneOf(sequencer).publish(34L);
                    inSequence(sequence);
                }
            });

        ringBuffer.publish(ringBuffer.next());
    }

    @Test
    public void shouldDelegateTryNextAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(
            new Expectations()
            {
                {
                    oneOf(sequencer).tryNext();
                    inSequence(sequence);
                    will(returnValue(34L));

                    oneOf(sequencer).publish(34L);
                    inSequence(sequence);
                }
            });

        ringBuffer.publish(ringBuffer.tryNext());
    }

    @Test
    public void shouldDelegateNextNAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(
            new Expectations()
            {
                {
                    oneOf(sequencer).next(10);
                    inSequence(sequence);
                    will(returnValue(34L));

                    oneOf(sequencer).publish(25L, 34L);
                    inSequence(sequence);
                }
            });

        long hi = ringBuffer.next(10);
        ringBuffer.publish(hi - 9, hi);
    }

    @Test
    public void shouldDelegateTryNextNAndPublish() throws Exception
    {
        final org.jmock.Sequence sequence = mockery.sequence("publication sequence");
        mockery.checking(
            new Expectations()
            {
                {
                    oneOf(sequencer).tryNext(10);
                    inSequence(sequence);
                    will(returnValue(34L));

                    oneOf(sequencer).publish(25L, 34L);
                    inSequence(sequence);
                }
            });

        long hi = ringBuffer.tryNext(10);
        ringBuffer.publish(hi - 9, hi);
    }
}
