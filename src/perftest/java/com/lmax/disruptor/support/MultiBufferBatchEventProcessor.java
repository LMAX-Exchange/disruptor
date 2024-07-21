package com.lmax.disruptor.support;

import com.lmax.disruptor.AlertException;
import com.lmax.disruptor.DataProvider;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.EventProcessor;
import com.lmax.disruptor.Sequence;
import com.lmax.disruptor.SequenceBarrier;
import com.lmax.disruptor.TimeoutException;

import java.util.concurrent.atomic.AtomicBoolean;

// 这个应该是一个可以支持多个 RingBuffer 消费的 EventProcessor
public class MultiBufferBatchEventProcessor<T>
    implements EventProcessor
{
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    // 支持多个DataProvider，即多个RingBuffer
    private final DataProvider<T>[] providers;
    private final SequenceBarrier[] barriers;
    private final EventHandler<T> handler;
    private final Sequence[] sequences;
    private long count;

    public MultiBufferBatchEventProcessor(
        final DataProvider<T>[] providers,
        final SequenceBarrier[] barriers,
        final EventHandler<T> handler)
    {
        if (providers.length != barriers.length)
        {
            throw new IllegalArgumentException();
        }

        this.providers = providers;
        this.barriers = barriers;
        this.handler = handler;

        this.sequences = new Sequence[providers.length];
        for (int i = 0; i < sequences.length; i++)
        {
            sequences[i] = new Sequence(-1);
        }
    }

    @Override
    public void run()
    {
        if (!isRunning.compareAndSet(false, true))
        {
            throw new RuntimeException("Already running");
        }

        for (SequenceBarrier barrier : barriers)
        {
            barrier.clearAlert();
        }

        final int barrierLength = barriers.length;

        while (true)
        {
            try
            {
                // 本质上是遍历 ringBuffer
                for (int i = 0; i < barrierLength; i++)
                {
                    // 取到可消费的最大序列
                    long available = barriers[i].waitFor(-1);
                    // 取到当前已消费的最大序列
                    Sequence sequence = sequences[i];

                    long nextSequence = sequence.get() + 1;

                    // 遍历区间内的 sequence，从 ringBuffer 处取到数据并消费
                    for (long l = nextSequence; l <= available; l++)
                    {
                        handler.onEvent(providers[i].get(l), l, nextSequence == available);
                    }

                    // 消费完了之后才会更新自己的已消费记录
                    sequence.set(available);

                    count += available - nextSequence + 1;
                }

                Thread.yield();
            }
            catch (AlertException e)
            {
                if (!isRunning())
                {
                    break;
                }
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (TimeoutException e)
            {
                e.printStackTrace();
            }
            catch (Exception e)
            {
                e.printStackTrace();
                break;
            }
        }
    }

    @Override
    public Sequence getSequence()
    {
        throw new UnsupportedOperationException();
    }

    public long getCount()
    {
        return count;
    }

    public Sequence[] getSequences()
    {
        return sequences;
    }

    @Override
    public void halt()
    {
        isRunning.set(false);
        barriers[0].alert();
    }

    @Override
    public boolean isRunning()
    {
        return isRunning.get();
    }
}
