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
 *
 * <p>回调接口，用于在{@link RingBuffer}中的事件可用时进行处理</p>
 *
 * @param <T> event implementation storing the data for sharing during exchange or parallel coordination of an event.
 * @see BatchEventProcessor#setExceptionHandler(ExceptionHandler) if you want to handle exceptions propagated out of the handler.
 */
public interface EventHandler<T> extends EventHandlerBase<T>
{
    /**
     * Called when a publisher has published an event to the {@link RingBuffer}.  The {@link BatchEventProcessor} will
     * read messages from the {@link RingBuffer} in batches, where a batch is all of the events available to be
     * processed without having to wait for any new event to arrive.  This can be useful for event handlers that need
     * to do slower operations like I/O as they can group together the data from multiple events into a single
     * operation.  Implementations should ensure that the operation is always performed when endOfBatch is true as
     * the time between that message and the next one is indeterminate.
     *
     * @param event      published to the {@link RingBuffer}
     * @param sequence   of the event being processed
     * @param endOfBatch flag to indicate if this is the last event in a batch from the {@link RingBuffer}
     * @throws Exception if the EventHandler would like the exception handled further up the chain.
     */
    @Override
    void onEvent(T event, long sequence, boolean endOfBatch) throws Exception;

    /**
     *  Used by the {@link BatchEventProcessor} to set a callback allowing the {@link EventHandler} to notify
     *  when it has finished consuming an event if this happens after the {@link EventHandler#onEvent(Object, long, boolean)} call.
     *
     *  <p>由{@link BatchEventProcessor}使用，用于设置回调，允许{@link EventHandler}在消耗事件后通知
     *  如果这发生在{@link EventHandler#onEvent(Object, long, boolean)}调用之后。</p>
     *
     *  <p>Typically this would be used when the handler is performing some sort of batching operation such as writing to an IO
     *  device; after the operation has completed, the implementation should call {@link Sequence#set} to update the
     *  sequence and allow other processes that are dependent on this handler to progress.
     *
     *  <p>通常在处理程序执行某种批处理操作（例如写入IO设备）时使用此方法；
     *  操作完成后，实现应调用{@link Sequence#set}来更新序列，并允许依赖于此处理程序的其他进程继续。</p>
     *
     * @param sequenceCallback callback on which to notify the {@link BatchEventProcessor} that the sequence has progressed.
     */
    default void setSequenceCallback(Sequence sequenceCallback)
    {
    }
}
