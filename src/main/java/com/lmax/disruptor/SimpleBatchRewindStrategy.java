package com.lmax.disruptor;

import java.util.Objects;

/**
 * Batch rewind strategy that always performs the specified {@link RewindAction}.
 */
public class SimpleBatchRewindStrategy implements BatchRewindStrategy
{

    private final RewindAction action;

    // Default constructor which retains current functionality
    public SimpleBatchRewindStrategy()
    {
        this(RewindAction.REWIND);
    }

    public SimpleBatchRewindStrategy(final RewindAction action)
    {
        this.action = Objects.requireNonNull(action, "action");
    }

    @Override
    public RewindAction handleRewindException(final RewindableException e, final int retriesAttempted)
    {
        return action;
    }
}
