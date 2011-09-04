package com.lmax.disruptor;

/**
 * Utility class for simplifying publication to the ring buffer.
 */
public class EventPublisher<E>
{
    private final RingBuffer<E> ringBuffer;

    /**
     * Construct from the ring buffer to be published to.
     * @param ringBuffer
     */
    public EventPublisher(RingBuffer<E> ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }
    
    /**
     * Publishes an event to the ring buffer.  It handles
     * claiming the next sequence, getting the current (uninitialized) 
     * event from the ring buffer and publishing the claimed sequence
     * after translation.
     * 
     * @param translator The user specified translation for the event
     */
    public void publishEvent(EventTranslator<E> translator)
    {
        long sequence = ringBuffer.nextSequence();
        try
        {
            translator.translateTo(ringBuffer.get(sequence), sequence);
        }
        finally
        {
            ringBuffer.publish(sequence);
        }
    }
}
