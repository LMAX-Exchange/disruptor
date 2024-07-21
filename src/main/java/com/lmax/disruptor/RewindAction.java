package com.lmax.disruptor;

/**
 * The result returned from the {@link BatchRewindStrategy} that decides whether to rewind or throw the exception
 *
 * <p>从{@link BatchRewindStrategy}返回的结果，用于决定是回滚还是抛出异常</p>
 */
public enum RewindAction
{
    /**
     * Rewind and replay the whole batch from  he beginning
     *
     * <p>从头开始回滚并重放整个批处理</p>
     */
    REWIND,

    /**
     * rethrows the exception, delegating it to the configured {@link ExceptionHandler}
     *
     * <p>重新抛出异常，将其委托给配置的{@link ExceptionHandler}</p>
     */
    THROW
}
