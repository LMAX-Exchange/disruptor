package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.lmax.disruptor.util.Util.awaitNanos;

/**
 * Variation of the {@link TimeoutBlockingWaitStrategy} that attempts to elide conditional wake-ups
 * when the lock is uncontended.
 *
 * <p>尝试省略条件唤醒的{@link TimeoutBlockingWaitStrategy}变体，当锁没有争用时。</p>
 */
public class LiteTimeoutBlockingWaitStrategy implements WaitStrategy
{
    private final Object mutex = new Object();
    private final AtomicBoolean signalNeeded = new AtomicBoolean(false);
    private final long timeoutInNanos;

    /**
     * @param timeout how long to wait before timing out
     * @param units the unit in which timeout is specified
     */
    public LiteTimeoutBlockingWaitStrategy(final long timeout, final TimeUnit units)
    {
        timeoutInNanos = units.toNanos(timeout);
    }

    @Override
    public long waitFor(
        final long sequence,
        final Sequence cursorSequence,
        final Sequence dependentSequence,
        final SequenceBarrier barrier)
        throws AlertException, InterruptedException, TimeoutException
    {
        long nanos = timeoutInNanos;

        long availableSequence;
        // 如果发布者的 sequence 值不够大，则阻塞指定的时间
        if (cursorSequence.get() < sequence)
        {
            synchronized (mutex)
            {
                while (cursorSequence.get() < sequence)
                {
                    signalNeeded.getAndSet(true);

                    barrier.checkAlert();
                    // 阻塞指定的时间，返回剩余的时间
                    nanos = awaitNanos(mutex, nanos);
                    // 如果剩余超时时间 <= 0，则说明一直把 nanos 时间都等完了，才醒过来；
                    // 则触发超时异常
                    if (nanos <= 0)
                    {
                        throw TimeoutException.INSTANCE;
                    }
                }
            }
        }

        // 如果依赖的消费者的 sequence 值不够大，则自旋等待；这里会一直 while
        while ((availableSequence = dependentSequence.get()) < sequence)
        {
            barrier.checkAlert();
        }

        return availableSequence;
    }

    @Override
    public void signalAllWhenBlocking()
    {
        if (signalNeeded.getAndSet(false))
        {
            synchronized (mutex)
            {
                mutex.notifyAll();
            }
        }
    }

    @Override
    public String toString()
    {
        return "LiteTimeoutBlockingWaitStrategy{" +
            "mutex=" + mutex +
            ", signalNeeded=" + signalNeeded +
            ", timeoutInNanos=" + timeoutInNanos +
            '}';
    }
}
