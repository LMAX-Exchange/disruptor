package com.lmax.disruptor.support;

import com.lmax.disruptor.BatchHandler;

public final class FizzBuzzHandler implements BatchHandler<FizzBuzzEntry>
{
    private final FizzBuzzStep fizzBuzzStep;
    private long fizzBuzzCounter = 0L;

    public FizzBuzzHandler(final FizzBuzzStep fizzBuzzStep)
    {
        this.fizzBuzzStep = fizzBuzzStep;
    }

    public void reset()
    {
        fizzBuzzCounter = 0L;
    }

    public long getFizzBuzzCounter()
    {
        return fizzBuzzCounter;
    }

    @Override
    public void onAvailable(final FizzBuzzEntry entry) throws Exception
    {
        switch (fizzBuzzStep)
        {
            case FIZZ:
                entry.setFizz(0 == (entry.getValue() % 3));
                break;

            case BUZZ:
                entry.setBuzz(0 == (entry.getValue() % 5));
                break;

            case FIZZ_BUZZ:
                if (entry.isFizz() && entry.isBuzz())
                {
                    ++fizzBuzzCounter;
                }
                break;
        }
    }

    @Override
    public void onEndOfBatch() throws Exception
    {
    }

    @Override
    public void onCompletion()
    {
    }
}
