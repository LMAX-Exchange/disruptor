package com.lmax.disruptor;

class SingleProducerPublisher implements Publisher
{
    private final WaitStrategy waitStrategy;
    private final Sequence cursor = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);

    public SingleProducerPublisher(WaitStrategy waitStrategy)
    {
        this.waitStrategy = waitStrategy;
    }

    @Override
    public void publish(long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    @Override
    public void ensureAvailable(long sequence)
    {
    }

    @Override
    public boolean isAvailable(long sequence)
    {
        return sequence <= cursor.get();
    }

    @Override
    public void forcePublish(long sequence)
    {
        cursor.set(sequence);
        waitStrategy.signalAllWhenBlocking();
    }

    Sequence getCursorSequence()
    {
        return cursor;
    }
}
