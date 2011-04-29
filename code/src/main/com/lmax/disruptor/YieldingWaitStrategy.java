package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

/**
 * Yielding strategy that uses a Thread.yield() for
 * {@link EventConsumer}s waiting on a barrier.
 *
 * This strategy is a good compromise between performance and CPU resource.
 */
public final class YieldingWaitStrategy implements WaitStrategy
{
    private final RingBuffer ringBuffer;
    private volatile boolean alerted = false;

    public YieldingWaitStrategy(final RingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public long waitFor(final long sequence)
        throws AlertException, InterruptedException
    {
        while (ringBuffer.getCursor() < sequence)
        {
            checkForAlert();
            Thread.yield();
        }

        return ringBuffer.getCursor();
    }

    @Override
    public long waitFor(final long sequence, final long timeout, final TimeUnit units)
        throws AlertException, InterruptedException
    {
        final long timeoutMs = units.convert(timeout, TimeUnit.MILLISECONDS);
        final long currentTime = System.currentTimeMillis();

        while (ringBuffer.getCursor() < sequence)
        {
            checkForAlert();
            Thread.yield();
            if (timeoutMs < System.currentTimeMillis() - currentTime)
            {
                break;
            }
        }

        return ringBuffer.getCursor();
    }

    @Override
    public void checkForAlert() throws AlertException
    {
        if (alerted)
        {
            alerted = false;
            throw new AlertException();
        }
    }

    @Override
    public void alert()
    {
        alerted = true;
        notifyConsumers();
    }

    @Override
    public void notifyConsumers()
    {
    }
}
