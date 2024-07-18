package myexapmples.fakepubconsume;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MyPubCon<T>
{
    List<Consumer<T>> consumerList = new ArrayList<>();
    AtomicBoolean started = new AtomicBoolean(false);
    LinkedBlockingQueue<T> queue = new LinkedBlockingQueue<>();

    public void addConsumer(Consumer consumer)
    {
        consumerList.add(consumer);
    }

    public void start()
    {
        if (started.compareAndSet(false, true))
        {
            for (final Consumer<T> consumer : consumerList)
            {
                Thread thread = new Thread(() ->
                {
                    while (true)
                    {
                        final T event;
                        try
                        {
                            event = queue.take();
                        }
                        catch (InterruptedException e)
                        {
                            throw new RuntimeException(e);
                        }
                        consumer.consume(event);
                    }

                });
                thread.start();
            }
        }
    }

    public void publish(T event)
    {
        queue.offer(event);
    }

}
