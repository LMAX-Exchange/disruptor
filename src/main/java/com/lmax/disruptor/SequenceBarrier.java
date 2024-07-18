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
 * Coordination barrier for tracking the cursor for publishers and sequence of
 * dependent {@link EventProcessor}s for processing a data structure
 *
 * <p>用于跟踪发布者的游标和依赖{@link EventProcessor}的序列的协调障碍，以处理数据结构</p>
 */
public interface SequenceBarrier
{
    /**
     * Wait for the given sequence to be available for consumption.
     *
     * <p>等待给定序列可供消费。</p>
     *
     * @param sequence to wait for
     * @return the sequence up to which is available
     * @throws AlertException       if a status change has occurred for the Disruptor
     * @throws InterruptedException if the thread needs awaking on a condition variable.
     * @throws TimeoutException     if a timeout occurs while waiting for the supplied sequence.
     */
    long waitFor(long sequence) throws AlertException, InterruptedException, TimeoutException;

    /**
     * Get the current cursor value that can be read.
     *
     * <p>获取可以读取的当前游标值。</p>
     *
     * @return value of the cursor for entries that have been published.
     */
    long getCursor();

    /**
     * The current alert status for the barrier.
     *
     * <p>判断是否处于警报状态。</p>
     *
     * @return true if in alert otherwise false.
     */
    boolean isAlerted();

    /**
     * Alert the {@link EventProcessor}s of a status change and stay in this status until cleared.
     *
     * <p>警报{@link EventProcessor}状态更改，并保持在此状态直到清除。</p>
     */
    void alert();

    /**
     * Clear the current alert status.
     *
     * <p>清除当前警报状态。</p>
     */
    void clearAlert();

    /**
     * Check if an alert has been raised and throw an {@link AlertException} if it has.
     *
     * <p>检查是否已发出警报，如果发出警报，则抛出{@link AlertException}。</p>
     *
     * @throws AlertException if alert has been raised.
     */
    void checkAlert() throws AlertException;
}
