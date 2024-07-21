/*
 * Copyright 2022 LMAX Ltd.
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
 * Callback interface to be implemented for processing events as they become available in the {@link RingBuffer}
 * with support for throwing a {@link RewindableException} when an even cannot be processed currently but may succeed on retry.
 *
 * <p>回调接口，用于在{@link RingBuffer}中处理事件时实现，支持在无法立即处理事件但可能在重试时成功时抛出{@link RewindableException}。</p>
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 * @see BatchEventProcessor#setExceptionHandler(ExceptionHandler) if you want to handle exceptions propagated out of the handler.
 */
public interface RewindableEventHandler<T> extends EventHandlerBase<T>
{
    /**
     * Called when a publisher has published an event to the {@link RingBuffer}.  The {@link BatchEventProcessor} will
     * read messages from the {@link RingBuffer} in batches, where a batch is all of the events available to be
     * processed without having to wait for any new event to arrive.  This can be useful for event handlers that need
     * to do slower operations like I/O as they can group together the data from multiple events into a single
     * operation.  Implementations should ensure that the operation is always performed when endOfBatch is true as
     * the time between that message and the next one is indeterminate.
     *
     * <p>当发布者将事件发布到{@link RingBuffer}时调用。
     * {@link BatchEventProcessor}将从{@link RingBuffer}中读取消息，其中批处理是所有可用于处理的事件，而无需等待任何新事件到达。
     * 对于需要执行较慢操作（如I/O）的事件处理程序， 这可能很有用，因为它们可以将多个事件的数据组合到单个操作中。
     * 实现应确保在endOfBatch为true时始终执行操作，因为该消息和下一个消息之间的时间是不确定的。</p>
     *
     * @param event      published to the {@link RingBuffer}
     * @param sequence   of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     * @throws RewindableException if the EventHandler would like the batch event processor to process the entire batch again.
     * @throws Exception if the EventHandler would like the exception handled further up the chain.
     */
    @Override
    void onEvent(T event, long sequence, boolean endOfBatch) throws RewindableException, Exception;
}
