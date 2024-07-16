package com.lmax.disruptor;

/**
 * Strategy for handling a rewindableException when processing an event.
 *
 * <p>用于处理 event 消费时抛出的 RewindableException 的策略。
 * RewindableException 是一个特殊的异常，表示当前的 event 无法处理，需要回滚到上一个 event 重新处理。
 */
public interface BatchRewindStrategy
{

    /**
     * When a {@link RewindableException} is thrown, this will be called.
     *
     * <p>当抛出 RewindableException 时，将调用此方法。
     *
     * @param e       the exception that propagated from the {@link EventHandler}.
     * @param attempts how many attempts there have been for the batch
     * @return the decision of whether to rewind the batch or throw the exception
     */
    RewindAction handleRewindException(RewindableException e, int attempts);
}
