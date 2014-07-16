package com.lmax.disruptor.example;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

public class TwoDisruptors
{
    private static class ValueEvent<T>
    {
        private T t;

        public T get()
        {
            return t;
        }

        public void set(final T t)
        {
            this.t = t;
        }
    }

    private static class Translator<T> implements EventTranslatorOneArg<ValueEvent<T>, ValueEvent<T>>
    {
        @Override
        public void translateTo(final ValueEvent<T> event, final long sequence, final ValueEvent<T> arg0)
        {
            event.set(arg0.get());
        }
    }

    private static class ValueEventHandler<T> implements EventHandler<ValueEvent<T>>
    {
        private final RingBuffer<ValueEvent<T>> ringBuffer;
        private final Translator<T> translator = new Translator<T>();

        public ValueEventHandler(final RingBuffer<ValueEvent<T>> ringBuffer)
        {
            this.ringBuffer = ringBuffer;
        }

        @Override
        public void onEvent(final ValueEvent<T> event, final long sequence, final boolean endOfBatch) throws Exception
        {
            ringBuffer.publishEvent(translator, event);
        }

        public static <T> EventFactory<ValueEvent<T>> factory()
        {
            return new EventFactory<ValueEvent<T>>()
            {
                @Override
                public ValueEvent<T> newInstance()
                {
                    return new ValueEvent<T>();
                }
            };
        }
    }

    @SuppressWarnings("unchecked")
    public static void main(final String[] args)
    {
        final Executor executor = Executors.newFixedThreadPool(2);
        final EventFactory<ValueEvent<String>> factory = ValueEventHandler.factory();

        final Disruptor<ValueEvent<String>> disruptorA =
                new Disruptor<ValueEvent<String>>(
                        factory,
                        1024,
                        executor,
                        ProducerType.MULTI,
                        new BlockingWaitStrategy());

        final Disruptor<ValueEvent<String>> disruptorB =
                new Disruptor<ValueEvent<String>>(
                        factory,
                        1024,
                        executor,
                        ProducerType.SINGLE,
                        new BlockingWaitStrategy());

        final ValueEventHandler<String> handlerA = new ValueEventHandler<String>(disruptorB.getRingBuffer());
        disruptorA.handleEventsWith(handlerA);

        final ValueEventHandler<String> handlerB = new ValueEventHandler<String>(disruptorA.getRingBuffer());
        disruptorB.handleEventsWith(handlerB);

        disruptorA.start();
        disruptorB.start();
    }
}
