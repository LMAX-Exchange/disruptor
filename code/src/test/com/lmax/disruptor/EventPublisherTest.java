package com.lmax.disruptor;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import com.lmax.disruptor.support.LongEvent;

public class EventPublisherTest implements EventTranslator<LongEvent>
{
    @Test
    public void shouldPublishEvent()
    {
        RingBuffer<LongEvent> ringBuffer = new RingBuffer<LongEvent>(LongEvent.FACTORY, 32);
        EventPublisher<LongEvent> eventPublisher = new EventPublisher<LongEvent>(ringBuffer);

        eventPublisher.publishEvent(this);
        eventPublisher.publishEvent(this);
        
        assertThat(ringBuffer.get(0).get(), is(0 + 29L));
        assertThat(ringBuffer.get(1).get(), is(1 + 29L));
    }
    
    @Override
    public LongEvent translateTo(LongEvent event, long sequence)
    {
        event.set(sequence + 29);
        return event;
    }
}
