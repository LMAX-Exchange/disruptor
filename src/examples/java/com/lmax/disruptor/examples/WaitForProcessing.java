package com.lmax.disruptor.examples;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslator;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.examples.support.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class WaitForProcessing
{
    public static class Consumer implements EventHandler<LongEvent>
    {
        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch)
        {

        }
    }

    public static void main(String[] args)
    {
        final Disruptor<LongEvent> disruptor = new Disruptor<>(
            LongEvent.FACTORY, 1024, DaemonThreadFactory.INSTANCE);

        Consumer firstConsumer = new Consumer();
        Consumer lastConsumer = new Consumer();
        disruptor.handleEventsWith(firstConsumer).then(lastConsumer);
        final RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        EventTranslator<LongEvent> translator = (event, sequence) -> event.set(sequence - 4);

        ringBuffer.tryPublishEvent(translator);

        waitForSpecificConsumer(disruptor, lastConsumer, ringBuffer);
        waitForRingBufferToBeIdle(ringBuffer);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private static void waitForRingBufferToBeIdle(RingBuffer<LongEvent> ringBuffer)
    {
        while (ringBuffer.getBufferSize() - ringBuffer.remainingCapacity() != 0)
        {
            // Wait for priocessing...
        }
    }

    private static void waitForSpecificConsumer(
        Disruptor<LongEvent> disruptor,
        Consumer lastConsumer,
        RingBuffer<LongEvent> ringBuffer)
    {
        long lastPublishedValue;
        long sequenceValueFor;
        do
        {
            lastPublishedValue = ringBuffer.getCursor();
            sequenceValueFor = disruptor.getSequenceValueFor(lastConsumer);
        }
        while (sequenceValueFor < lastPublishedValue);
    }
}
