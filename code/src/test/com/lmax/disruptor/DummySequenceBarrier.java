package com.lmax.disruptor;

import java.util.concurrent.TimeUnit;

class DummySequenceBarrier implements SequenceBarrier
{
    @Override
    public long waitFor(long sequence) throws AlertException, InterruptedException
    {
        return 0;
    }

    @Override
    public long waitFor(long sequence, long timeout, TimeUnit units) throws AlertException, InterruptedException
    {
        return 0;
    }

    @Override
    public long getCursor()
    {
        return 0;
    }

    @Override
    public boolean isAlerted()
    {
        return false;
    }

    @Override
    public void alert()
    {
    }

    @Override
    public void clearAlert()
    {
    }

    @Override
    public void checkAlert() throws AlertException
    {
    }
}