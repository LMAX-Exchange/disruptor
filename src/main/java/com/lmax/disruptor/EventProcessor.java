/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

/**
 * An EventProcessor needs to be an implementation of a runnable that will poll for events from the {@link RingBuffer}
 * using the appropriate wait strategy.  It is unlikely that you will need to implement this interface yourself.
 * Look at using the {@link EventHandler} interface along with the pre-supplied BatchEventProcessor in the first
 * instance.
 *
 * <p>EventProcessor需要是一个可运行的实现，它将使用适当的等待策略从{@link RingBuffer}中轮询事件。
 * 您不太可能需要自己实现此接口。首先查看使用{@link EventHandler}接口以及预先提供的BatchEventProcessor。</p>
 *
 * <p>An EventProcessor will generally be associated with a Thread for execution.
 *
 * <p>EventProcessor通常将与线程关联以执行。</p>
 */
public interface EventProcessor extends Runnable
{
    /**
     * Get a reference to the {@link Sequence} being used by this {@link EventProcessor}.
     *
     * <p>获取此{@link EventProcessor}使用的{@link Sequence}的引用。</p>
     *
     * @return reference to the {@link Sequence} for this {@link EventProcessor}
     */
    Sequence getSequence();

    /**
     * Signal that this EventProcessor should stop when it has finished consuming at the next clean break.
     * It will call {@link SequenceBarrier#alert()} to notify the thread to check status.
     *
     * <p>表示此EventProcessor应在下一个干净的中断时停止消耗。
     * 它将调用{@link SequenceBarrier#alert()}来通知线程检查状态。</p>
     */
    void halt();

    /**
     * @return whether this event processor is running or not
     * Implementations should ideally return false only when the associated thread is idle.
     *
     * <p>返回此事件处理器是否正在运行
     * 实现应该在关联线程空闲时才返回false。</p>
     */
    boolean isRunning();
}
