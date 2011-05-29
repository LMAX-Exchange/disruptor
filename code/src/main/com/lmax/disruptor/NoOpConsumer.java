package com.lmax.disruptor;

/**
 * No operation version of a {@link Consumer} that simply tracks a {@link RingBuffer}.
 * This is useful in tests or for pre-filling a {@link RingBuffer} from a producer.
 */
public final class NoOpConsumer implements Consumer
{
    private final RingBuffer ringBuffer;

    /**
     * Construct a {@link Consumer} that simply tracks a {@link RingBuffer}.
     *
     * @param ringBuffer to track.
     */
    public NoOpConsumer(final RingBuffer ringBuffer)
    {
        this.ringBuffer = ringBuffer;
    }

    @Override
    public long getSequence()
    {
        return ringBuffer.getCursor();
    }

    @Override
    public void halt()
    {
    }

    @Override
    public void run()
    {
    }
}
