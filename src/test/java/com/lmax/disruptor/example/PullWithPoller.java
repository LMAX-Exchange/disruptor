package com.lmax.disruptor.example;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;

/**
 * Created by barkerm on 02/02/15.
 */
public class PullWithPoller
{
    public static class DataEvent<T>
    {
        T data;

        public static <T> EventFactory<DataEvent<T>> factory()
        {
            return new EventFactory<DataEvent<T>>()
            {
                @Override
                public DataEvent<T> newInstance()
                {
                    return new DataEvent<T>();
                }
            };
        }

        public T copyOfData()
        {
            // Copy the data out here.  In this case we have a single reference object, so the pass by
            // reference is sufficient.  But if we were reusing a byte array, then we would need to copy
            // the actual contents.
            return data;
        }
    }

    public static void main(String[] args) throws Exception
    {
        RingBuffer<DataEvent<Object>> ringBuffer = RingBuffer.createMultiProducer(DataEvent.factory(), 1024);

        final EventPoller<DataEvent<Object>> poller = ringBuffer.newPoller();

        Object value = getNextValue(poller);

        // Value could be null if no events are available.
        if (null != value)
        {
            // Process value.
        }
    }

    private static Object getNextValue(EventPoller<DataEvent<Object>> poller) throws Exception
    {
        final Object[] out = new Object[1];

        poller.poll(
            new EventPoller.Handler<DataEvent<Object>>()
            {
                @Override
                public boolean onEvent(DataEvent<Object> event, long sequence, boolean endOfBatch) throws Exception
                {
                    out[0] = event.copyOfData();

                    // Return false so that only one event is processed at a time.
                    return false;
                }
            });

        return out[0];
    }
}
