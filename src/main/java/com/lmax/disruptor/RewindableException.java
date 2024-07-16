package com.lmax.disruptor;

/**
 * A special exception that can be thrown while using the {@link BatchEventProcessor}.
 * On throwing this exception the {@link BatchEventProcessor} can choose to rewind and replay the batch or throw
 * depending on the {@link BatchRewindStrategy}
 *
 * <p>在使用 BatchEventProcessor 时可以抛出的特殊异常。
 * 抛出此异常后，BatchEventProcessor 可以选择回滚并重放 batch，也可以选择抛出异常，具体取决于 BatchRewindStrategy。
 */
public class RewindableException extends Throwable
{

    /*
        RewindableException 是 Disruptor 框架中的一个异常类，用于处理在事件处理过程中可能需要重新处理的异常情况。
        它允许事件处理器在遇到某些特定异常时，可以选择重新处理当前事件，而不是简单地跳过或终止处理。

        主要用途：
        1. 重新处理事件：当事件处理器在处理某个事件时遇到 RewindableException，可以选择重新处理该事件。
                       这对于某些需要确保事件被成功处理的场景非常有用。
        2. 错误恢复：在某些情况下，错误可能是暂时的，通过重试可以成功完成操作。
                    RewindableException 提供了一种机制，使得事件处理器可以尝试重新执行失败的操作。

        工作原理：
        当事件处理器抛出 RewindableException 时，Disruptor 框架会捕获这个异常，并允许事件处理器重新尝试处理当前事件。
        具体的实现和行为可能依赖于 Disruptor 的配置和使用场景。
     */

    /**
     * @param cause The underlying cause of the exception.
     */
    public RewindableException(final Throwable cause)
    {
        super("REWINDING BATCH", cause);
    }
}
