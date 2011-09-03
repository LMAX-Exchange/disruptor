package com.lmax.disruptor;

public class EventPublisher<E>
{
    private final RingBuffer<E> ringBuffer;

    public EventPublisher(RingBuffer<E> ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }
    
    public void publishEvent(EventTranslator<E> translator)
    {
        long sequence = ringBuffer.nextSequence();
        E event = ringBuffer.get(sequence);
        try
        {
            translator.translateTo(event, sequence);
        }
        finally
        {
            ringBuffer.publish(sequence);
        }
    }
}
