package com.lmax.disruptor.example;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorThreeArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;

import java.util.concurrent.Executors;

public class MultiProducerWithTranslator
{
    private static class IMessage
    {}

    private static class ITransportable
    {}

    private static class ObjectBox
    {
        IMessage message;
        ITransportable transportable;
        String string;

        private static final EventFactory<ObjectBox> FACTORY = new EventFactory<ObjectBox>()
        {
            @Override
            public ObjectBox newInstance()
            {
                return new ObjectBox();
            }
        };

        public void setMessage(IMessage arg0)
        {
            message = arg0;
        }

        public void setTransportable(ITransportable arg1)
        {
            transportable = arg1;
        }

        public void setStreamName(String arg2)
        {
            string = arg2;
        }
    }

    public static class Publisher implements EventTranslatorThreeArg<ObjectBox, IMessage, ITransportable, String>
    {
        @Override
        public void translateTo(ObjectBox event, long sequence, IMessage arg0, ITransportable arg1, String arg2)
        {
            event.setMessage(arg0);
            event.setTransportable(arg1);
            event.setStreamName(arg2);
        }
    }

    public static class Consumer implements EventHandler<ObjectBox>
    {
        @Override
        public void onEvent(ObjectBox event, long sequence, boolean endOfBatch) throws Exception
        {

        }
    }

    public static void main(String[] args) throws InterruptedException
    {
        Disruptor<ObjectBox> disruptor = new Disruptor<ObjectBox>(ObjectBox.FACTORY, 1024, Executors.newCachedThreadPool());
        disruptor.handleEventsWith(new Consumer());

        disruptor.start();
        final RingBuffer<ObjectBox> ringBuffer = disruptor.getRingBuffer();

        Publisher p = new Publisher();
        IMessage message = new IMessage();
        ITransportable transportable = new ITransportable();
        String streamName = "com.lmax.wibble";

        while (true)
        {
            ringBuffer.publishEvent(p, message, transportable, streamName);
//            Thread.sleep(1000);
        }
    }
}
