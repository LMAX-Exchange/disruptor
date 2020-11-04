package com.lmax.disruptor;

import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.support.LongEvent;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

public class BatchingTest
{
    private static class ParallelEventHandler implements EventHandler<LongEvent>
    {
        private final long mask;
        private final long ordinal;
        private final int batchSize = 10;

        private long eventCount;
        private long batchCount;
        private long publishedValue;
        private long tempValue;
        private volatile long processed;

        ParallelEventHandler(long mask, long ordinal)
        {
            this.mask = mask;
            this.ordinal = ordinal;
        }

        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception
        {
            if ((sequence & mask) == ordinal)
            {
                eventCount++;
                tempValue = event.get();
            }

            if (endOfBatch || ++batchCount >= batchSize)
            {
                publishedValue = tempValue;
                batchCount = 0;
            }
            else
            {
                LockSupport.parkNanos(1);
            }

            processed = sequence;
        }
    }

    private static Stream<Arguments> generateData()
    {
        return Stream.of(arguments(ProducerType.MULTI), arguments(ProducerType.SINGLE));
    }


    @ParameterizedTest
    @MethodSource("generateData")
    public void shouldBatch(ProducerType producerType) throws Exception
    {
        Disruptor<LongEvent> d = new Disruptor<>(
                LongEvent.FACTORY, 2048, DaemonThreadFactory.INSTANCE,
                producerType, new SleepingWaitStrategy());

        ParallelEventHandler handler1 = new ParallelEventHandler(1, 0);
        ParallelEventHandler handler2 = new ParallelEventHandler(1, 1);

        d.handleEventsWith(handler1, handler2);

        RingBuffer<LongEvent> buffer = d.start();

        EventTranslator<LongEvent> translator = LongEvent::set;

        int eventCount = 10000;
        for (int i = 0; i < eventCount; i++)
        {
            buffer.publishEvent(translator);
        }

        while (handler1.processed != eventCount - 1 ||
                handler2.processed != eventCount - 1)
        {
            Thread.sleep(1);
        }

        assertEquals(eventCount - 2, handler1.publishedValue);
        assertEquals(eventCount / 2, handler1.eventCount);
        assertEquals(eventCount - 1, handler2.publishedValue);
        assertEquals(eventCount / 2, handler2.eventCount);
    }
}
