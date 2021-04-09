package com.lmax.disruptor.examples;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

public class MultiProducerWithTranslator
{
    private static class IMessage
    {
    }

    private static class ITransportable
    {
    }

    private static class ObjectBox
    {
        IMessage message;
        ITransportable transportable;
        String string;

        private static final EventFactory<ObjectBox> FACTORY = ObjectBox::new;

        public void setMessage(final IMessage arg0)
        {
            message = arg0;
        }

        public void setTransportable(final ITransportable arg1)
        {
            transportable = arg1;
        }

        public void setStreamName(final String arg2)
        {
            string = arg2;
        }
    }

    public static class Publisher implements EventTranslatorThreeArg<ObjectBox, IMessage, ITransportable, String>
    {
        @Override
        public void translateTo(final ObjectBox event, final long sequence, final IMessage arg0, final ITransportable arg1, final String arg2)
        {
            event.setMessage(arg0);
            event.setTransportable(arg1);
            event.setStreamName(arg2);
        }
    }

    public static class Consumer implements EventHandler<ObjectBox>
    {
        @Override
        public void onEvent(final ObjectBox event, final long sequence, final boolean endOfBatch)
        {

        }
    }

    static final int RING_SIZE = 1024;

    public static void main(final String[] args) throws InterruptedException
    {
        Disruptor<ObjectBox> disruptor = new Disruptor<>(
                ObjectBox.FACTORY, RING_SIZE, DaemonThreadFactory.INSTANCE, ProducerType.MULTI,
                new BlockingWaitStrategy());
        disruptor.handleEventsWith(new Consumer()).then(new Consumer());
        final RingBuffer<ObjectBox> ringBuffer = disruptor.getRingBuffer();
        Publisher p = new Publisher();
        IMessage message = new IMessage();
        ITransportable transportable = new ITransportable();
        String streamName = "com.lmax.wibble";
        System.out.println("publishing " + RING_SIZE + " messages");
        for (int i = 0; i < RING_SIZE; i++)
        {
            ringBuffer.publishEvent(p, message, transportable, streamName);
            Thread.sleep(10);
        }
        System.out.println("start disruptor");
        disruptor.start();
        System.out.println("continue publishing");
        while (true)
        {
            ringBuffer.publishEvent(p, message, transportable, streamName);
            Thread.sleep(10);
        }
    }
}
