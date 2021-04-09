package com.lmax.disruptor.examples;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.RingBuffer;

/**
 * Alternative usage of EventPoller, here we wrap it around BatchedEventPoller
 * to achieve Disruptor's batching. this speeds up the polling feature
 */
public class PullWithBatchedPoller
{
    public static void main(final String[] args) throws Exception
    {
        int batchSize = 40;
        RingBuffer<BatchedPoller.DataEvent<Object>> ringBuffer =
                RingBuffer.createMultiProducer(BatchedPoller.DataEvent.factory(), 1024);

        BatchedPoller<Object> poller = new BatchedPoller<>(ringBuffer, batchSize);

        Object value = poller.poll();

        // Value could be null if no events are available.
        if (null != value)
        {
            // Process value.
        }
    }

    static class BatchedPoller<T>
    {
        private final EventPoller<DataEvent<T>> poller;
        private final BatchedData<T> polledData;

        BatchedPoller(final RingBuffer<DataEvent<T>> ringBuffer, final int batchSize)
        {
            this.poller = ringBuffer.newPoller();
            ringBuffer.addGatingSequences(poller.getSequence());
            this.polledData = new BatchedData<>(batchSize);
        }

        public T poll() throws Exception
        {
            if (polledData.getMsgCount() > 0)
            {
                return polledData.pollMessage(); // we just fetch from our local
            }

            loadNextValues(poller, polledData); // we try to load from the ring
            return polledData.getMsgCount() > 0 ? polledData.pollMessage() : null;
        }

        private EventPoller.PollState loadNextValues(final EventPoller<DataEvent<T>> poller, final BatchedData<T> batch)
                throws Exception
        {
            return poller.poll((event, sequence, endOfBatch) ->
            {
                T item = event.copyOfData();
                return item != null ? batch.addDataItem(item) : false;
            });
        }

        public static class DataEvent<T>
        {
            T data;

            public static <T> EventFactory<DataEvent<T>> factory()
            {
                return DataEvent::new;
            }

            public T copyOfData()
            {
                // Copy the data out here. In this case we have a single reference
                // object, so the pass by
                // reference is sufficient. But if we were reusing a byte array,
                // then we
                // would need to copy
                // the actual contents.
                return data;
            }

            void set(final T d)
            {
                data = d;
            }
        }

        private static class BatchedData<T>
        {
            private int msgHighBound;
            private final int capacity;
            private final T[] data;
            private int cursor;

            @SuppressWarnings("unchecked")
            BatchedData(final int size)
            {
                this.capacity = size;
                data = (T[]) new Object[this.capacity];
            }

            private void clearCount()
            {
                msgHighBound = 0;
                cursor = 0;
            }

            public int getMsgCount()
            {
                return msgHighBound - cursor;
            }

            public boolean addDataItem(final T item) throws IndexOutOfBoundsException
            {
                if (msgHighBound >= capacity)
                {
                    throw new IndexOutOfBoundsException("Attempting to add item to full batch");
                }

                data[msgHighBound++] = item;
                return msgHighBound < capacity;
            }

            public T pollMessage()
            {
                T rtVal = null;
                if (cursor < msgHighBound)
                {
                    rtVal = data[cursor++];
                }
                if (cursor > 0 && cursor >= msgHighBound)
                {
                    clearCount();
                }
                return rtVal;
            }
        }
    }
}
